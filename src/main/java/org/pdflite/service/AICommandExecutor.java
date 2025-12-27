package org.pdflite.service;

import javafx.application.Platform;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.pdflite.manager.BookmarkManager;
import org.pdflite.manager.LanguageManager;
import org.pdflite.model.AICommand;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Executes AI commands on PDF documents.
 */
public class AICommandExecutor {
    private static final Logger logger = LoggerFactory.getLogger(AICommandExecutor.class);

    private static LanguageManager lang() {
        return LanguageManager.getInstance();
    }

    private final PDFSplitService splitService;
    private final PDFReorderService reorderService;
    private final PDFService pdfService;
    private final Supplier<PDFDocument> documentSupplier;
    private final Supplier<BookmarkManager> bookmarkManagerSupplier;
    private final IntConsumer navigateToPage;
    private final Consumer<String> statusCallback;
    private final Runnable refreshCallback;
    private final Supplier<Stage> stageSupplier;

    public AICommandExecutor(
            Supplier<PDFDocument> documentSupplier,
            Supplier<BookmarkManager> bookmarkManagerSupplier,
            IntConsumer navigateToPage,
            Consumer<String> statusCallback,
            Runnable refreshCallback,
            Supplier<Stage> stageSupplier) {
        this.splitService = new PDFSplitService();
        this.reorderService = new PDFReorderService();
        this.pdfService = new PDFService();
        this.documentSupplier = documentSupplier;
        this.bookmarkManagerSupplier = bookmarkManagerSupplier;
        this.navigateToPage = navigateToPage;
        this.statusCallback = statusCallback;
        this.refreshCallback = refreshCallback;
        this.stageSupplier = stageSupplier;
    }

    /**
     * Executes an AI command and returns the result message.
     */
    public CompletableFuture<String> execute(AICommand command) {
        PDFDocument doc = documentSupplier.get();
        
        if (doc == null && command.getAction() != AICommand.Action.HELP 
                && command.getAction() != AICommand.Action.UNKNOWN) {
            return CompletableFuture.completedFuture(lang().getString("ai.openPdfFirst"));
        }

        return switch (command.getAction()) {
            case SPLIT, EXTRACT -> executeSplit(command, doc);
            case DELETE -> executeDelete(command, doc);
            case REORDER -> executeReorder(command, doc);
            case SWAP -> executeSwap(command, doc);
            case MOVE -> executeMove(command, doc);
            case BOOKMARK -> executeBookmark(command, doc);
            case GOTO -> executeGoto(command, doc);
            case READTEXT -> executeReadText(command, doc);
            case SUMMARIZE -> executeSummarize(command, doc);
            case INFO -> executeInfo(doc);
            case HELP -> executeHelp();
            case UNKNOWN -> CompletableFuture.completedFuture(command.getMessage());
        };
    }

    private CompletableFuture<String> executeSplit(AICommand command, PDFDocument doc) {
        List<Integer> pages = command.getPages();
        if (pages.isEmpty()) {
            return CompletableFuture.completedFuture(lang().getString("ai.specifyPages"));
        }

        // Validate pages
        int totalPages = doc.getTotalPages();
        for (int page : pages) {
            if (page < 1 || page > totalPages) {
                return CompletableFuture.completedFuture(
                        MessageFormat.format(lang().getString("ai.invalidPage"), page, totalPages));
            }
        }

        CompletableFuture<String> result = new CompletableFuture<>();

        Platform.runLater(() -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(lang().getString("ai.selectOutputDir"));
            File outputDir = chooser.showDialog(stageSupplier.get());

            if (outputDir == null) {
                result.complete(lang().getString("ai.operationCancelled"));
                return;
            }

            try {
                String outputName = command.getOutputName();
                if (outputName == null || outputName.isEmpty()) {
                    outputName = "extracted_pages.pdf";
                }
                if (!outputName.endsWith(".pdf")) {
                    outputName += ".pdf";
                }

                // Create page range
                int minPage = pages.stream().min(Integer::compareTo).orElse(1);
                int maxPage = pages.stream().max(Integer::compareTo).orElse(1);
                
                List<PDFSplitService.PageRange> ranges = new ArrayList<>();
                ranges.add(new PDFSplitService.PageRange(minPage, maxPage, outputName));

                List<File> outputFiles = splitService.splitPDF(
                        doc.getDocument(), outputDir, ranges);

                statusCallback.accept(MessageFormat.format(lang().getString("ai.splitSuccess"), pages.size()));
                result.complete(MessageFormat.format(lang().getString("ai.splitSuccessMsg"), 
                        minPage, maxPage, outputFiles.get(0).getName()));

            } catch (Exception e) {
                logger.error("Error splitting PDF", e);
                result.complete(MessageFormat.format(lang().getString("ai.splitError"), e.getMessage()));
            }
        });

        return result;
    }

    private CompletableFuture<String> executeDelete(AICommand command, PDFDocument doc) {
        List<Integer> pages = command.getPages();
        if (pages.isEmpty()) {
            return CompletableFuture.completedFuture(lang().getString("ai.specifyPagesToDelete"));
        }

        int totalPages = doc.getTotalPages();
        
        // Validate
        for (int page : pages) {
            if (page < 1 || page > totalPages) {
                return CompletableFuture.completedFuture(
                        MessageFormat.format(lang().getString("ai.invalidPage"), page, totalPages));
            }
        }

        if (pages.size() >= totalPages) {
            return CompletableFuture.completedFuture(lang().getString("ai.cannotDeleteAllPages"));
        }

        try {
            // Sort descending to delete from end first
            List<Integer> sortedPages = pages.stream()
                    .sorted((a, b) -> b - a)
                    .toList();

            for (int page : sortedPages) {
                doc.getDocument().removePage(page - 1); // Convert to 0-based
            }

            doc.clearCache();
            Platform.runLater(refreshCallback);
            statusCallback.accept(MessageFormat.format(lang().getString("ai.deletedPages"), pages.size()));

            return CompletableFuture.completedFuture(
                    MessageFormat.format(lang().getString("ai.deletedPagesMsg"), pages.size(), pages));

        } catch (Exception e) {
            logger.error("Error deleting pages", e);
            return CompletableFuture.completedFuture(MessageFormat.format(lang().getString("ai.deleteError"), e.getMessage()));
        }
    }

    private CompletableFuture<String> executeReorder(AICommand command, PDFDocument doc) {
        List<Integer> newOrder = command.getNewOrder();
        if (newOrder.isEmpty()) {
            return CompletableFuture.completedFuture(lang().getString("ai.specifyNewOrder"));
        }

        int totalPages = doc.getTotalPages();
        
        if (newOrder.size() != totalPages) {
            return CompletableFuture.completedFuture(
                    MessageFormat.format(lang().getString("ai.orderMismatch"), newOrder.size(), totalPages));
        }

        // Convert to 0-based
        List<Integer> zeroBasedOrder = newOrder.stream()
                .map(p -> p - 1)
                .toList();

        try {
            reorderService.reorderPages(doc, zeroBasedOrder);
            Platform.runLater(refreshCallback);
            statusCallback.accept(lang().getString("ai.reorderedPages"));

            return CompletableFuture.completedFuture(
                    MessageFormat.format(lang().getString("ai.reorderedPagesMsg"), newOrder));

        } catch (Exception e) {
            logger.error("Error reordering pages", e);
            return CompletableFuture.completedFuture(MessageFormat.format(lang().getString("ai.reorderError"), e.getMessage()));
        }
    }

    private CompletableFuture<String> executeSwap(AICommand command, PDFDocument doc) {
        int page1 = command.getPage1();
        int page2 = command.getPage2();

        if (page1 == -1 || page2 == -1) {
            return CompletableFuture.completedFuture(lang().getString("ai.specifyTwoPages"));
        }

        int totalPages = doc.getTotalPages();
        if (page1 < 1 || page1 > totalPages || page2 < 1 || page2 > totalPages) {
            return CompletableFuture.completedFuture(
                    MessageFormat.format(lang().getString("ai.invalidPageDoc"), totalPages));
        }

        if (page1 == page2) {
            return CompletableFuture.completedFuture(lang().getString("ai.pagesMustDiffer"));
        }

        try {
            // Create new order with swapped pages
            List<Integer> newOrder = new ArrayList<>();
            for (int i = 0; i < totalPages; i++) {
                newOrder.add(i);
            }
            
            // Swap positions (0-based)
            int idx1 = page1 - 1;
            int idx2 = page2 - 1;
            newOrder.set(idx1, idx2);
            newOrder.set(idx2, idx1);

            reorderService.reorderPages(doc, newOrder);
            Platform.runLater(refreshCallback);
            statusCallback.accept(MessageFormat.format(lang().getString("ai.swappedPages"), page1, page2));

            return CompletableFuture.completedFuture(
                    MessageFormat.format(lang().getString("ai.swappedPagesMsg"), page1, page2));

        } catch (Exception e) {
            logger.error("Error swapping pages", e);
            return CompletableFuture.completedFuture(MessageFormat.format(lang().getString("ai.swapError"), e.getMessage()));
        }
    }

    private CompletableFuture<String> executeMove(AICommand command, PDFDocument doc) {
        int fromPage = command.getFromPage();
        int toPage = command.getToPage();

        if (fromPage == -1 || toPage == -1) {
            return CompletableFuture.completedFuture(lang().getString("ai.specifySourceDest"));
        }

        int totalPages = doc.getTotalPages();
        if (fromPage < 1 || fromPage > totalPages || toPage < 1 || toPage > totalPages) {
            return CompletableFuture.completedFuture(
                    MessageFormat.format(lang().getString("ai.invalidPageDoc"), totalPages));
        }

        if (fromPage == toPage) {
            return CompletableFuture.completedFuture(lang().getString("ai.sourceDestMustDiffer"));
        }

        try {
            reorderService.movePage(doc, fromPage - 1, toPage - 1);
            Platform.runLater(refreshCallback);
            statusCallback.accept(MessageFormat.format(lang().getString("ai.movedPage"), fromPage, toPage));

            return CompletableFuture.completedFuture(
                    MessageFormat.format(lang().getString("ai.movedPageMsg"), fromPage, toPage));

        } catch (Exception e) {
            logger.error("Error moving page", e);
            return CompletableFuture.completedFuture(MessageFormat.format(lang().getString("ai.moveError"), e.getMessage()));
        }
    }

    private CompletableFuture<String> executeBookmark(AICommand command, PDFDocument doc) {
        String title = command.getTitle();
        if (title == null || title.isEmpty()) {
            return CompletableFuture.completedFuture(lang().getString("ai.specifyBookmarkTitle"));
        }

        int page = command.getPage();
        if (page == -1) {
            page = doc.getCurrentPage() + 1; // Use current page (convert to 1-based for display)
        }

        BookmarkManager bookmarkManager = bookmarkManagerSupplier.get();
        if (bookmarkManager == null) {
            return CompletableFuture.completedFuture(lang().getString("ai.bookmarkManagerUnavailable"));
        }

        int finalPage = page;
        bookmarkManager.addBookmark(finalPage - 1, title); // Convert to 0-based
        statusCallback.accept(lang().getString("ai.addedBookmark"));

        return CompletableFuture.completedFuture(
                MessageFormat.format(lang().getString("ai.addedBookmarkMsg"), title, finalPage));
    }

    private CompletableFuture<String> executeGoto(AICommand command, PDFDocument doc) {
        int page = command.getPage();
        if (page < 1 || page > doc.getTotalPages()) {
            return CompletableFuture.completedFuture(
                    MessageFormat.format(lang().getString("ai.invalidPage"), page, doc.getTotalPages()));
        }

        Platform.runLater(() -> navigateToPage.accept(page - 1)); // Convert to 0-based
        return CompletableFuture.completedFuture(MessageFormat.format(lang().getString("ai.gotoPage"), page));
    }

    private CompletableFuture<String> executeReadText(AICommand command, PDFDocument doc) {
        List<Integer> pages = command.getPages();
        
        // Default to current page if no pages specified
        if (pages.isEmpty()) {
            pages = List.of(doc.getCurrentPage() + 1);
        }

        int totalPages = doc.getTotalPages();
        StringBuilder result = new StringBuilder();

        try {
            for (int page : pages) {
                if (page < 1 || page > totalPages) {
                    continue;
                }
                
                String text = pdfService.extractTextFromPage(doc, page - 1);
                if (text != null && !text.trim().isEmpty()) {
                    if (pages.size() > 1) {
                        result.append(MessageFormat.format(lang().getString("ai.pageText"), page)).append("\n");
                    }
                    // Limit text length to avoid too long response
                    String trimmedText = text.trim();
                    if (trimmedText.length() > 1000) {
                        trimmedText = trimmedText.substring(0, 1000) + "...\n" + lang().getString("ai.textTruncated");
                    }
                    result.append(trimmedText).append("\n\n");
                }
            }

            if (result.isEmpty()) {
                return CompletableFuture.completedFuture(lang().getString("ai.noTextFound"));
            }

            return CompletableFuture.completedFuture(result.toString().trim());

        } catch (Exception e) {
            logger.error("Error reading text from PDF", e);
            return CompletableFuture.completedFuture(MessageFormat.format(lang().getString("ai.readTextError"), e.getMessage()));
        }
    }

    private CompletableFuture<String> executeSummarize(AICommand command, PDFDocument doc) {
        List<Integer> pages = command.getPages();
        
        // Default to current page if no pages specified
        if (pages.isEmpty()) {
            pages = List.of(doc.getCurrentPage() + 1);
        }

        int totalPages = doc.getTotalPages();
        StringBuilder textContent = new StringBuilder();

        try {
            for (int page : pages) {
                if (page < 1 || page > totalPages) {
                    continue;
                }
                
                String text = pdfService.extractTextFromPage(doc, page - 1);
                if (text != null && !text.trim().isEmpty()) {
                    textContent.append(text.trim()).append("\n\n");
                }
            }

            if (textContent.isEmpty()) {
                return CompletableFuture.completedFuture(lang().getString("ai.noTextToSummarize"));
            }

            // Limit text for API (max ~3000 chars to avoid token limit)
            String content = textContent.toString();
            if (content.length() > 3000) {
                content = content.substring(0, 3000);
            }

            // Return text with AI's summary from message
            String aiSummary = command.getMessage();
            if (aiSummary != null && !aiSummary.isEmpty()) {
                return CompletableFuture.completedFuture(aiSummary);
            }
            
            return CompletableFuture.completedFuture(lang().getString("ai.readButCannotSummarize"));

        } catch (Exception e) {
            logger.error("Error summarizing PDF", e);
            return CompletableFuture.completedFuture(MessageFormat.format(lang().getString("ai.summarizeError"), e.getMessage()));
        }
    }

    private CompletableFuture<String> executeInfo(PDFDocument doc) {
        StringBuilder info = new StringBuilder();
        info.append(lang().getString("ai.docInfo")).append("\n");
        info.append(MessageFormat.format(lang().getString("ai.docFile"), 
                doc.getFile() != null ? doc.getFile().getName() : lang().getString("ai.docNotSaved"))).append("\n");
        info.append(MessageFormat.format(lang().getString("ai.docTotalPages"), doc.getTotalPages())).append("\n");
        info.append(MessageFormat.format(lang().getString("ai.docCurrentPage"), doc.getCurrentPage() + 1)).append("\n");
        
        return CompletableFuture.completedFuture(info.toString());
    }

    private CompletableFuture<String> executeHelp() {
        return CompletableFuture.completedFuture(lang().getString("ai.helpText"));
    }
}
