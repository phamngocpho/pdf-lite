package org.pdflite.service;

import javafx.application.Platform;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.pdflite.manager.BookmarkManager;
import org.pdflite.model.AICommand;
import org.pdflite.model.PDFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
            return CompletableFuture.completedFuture("Vui lòng mở một file PDF trước.");
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
            return CompletableFuture.completedFuture("Vui lòng chỉ định các trang cần tách.");
        }

        // Validate pages
        int totalPages = doc.getTotalPages();
        for (int page : pages) {
            if (page < 1 || page > totalPages) {
                return CompletableFuture.completedFuture(
                        "Trang " + page + " không hợp lệ. Document có " + totalPages + " trang.");
            }
        }

        CompletableFuture<String> result = new CompletableFuture<>();

        Platform.runLater(() -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Chọn thư mục lưu file");
            File outputDir = chooser.showDialog(stageSupplier.get());

            if (outputDir == null) {
                result.complete("Đã hủy thao tác.");
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

                statusCallback.accept("Đã tách " + pages.size() + " trang");
                result.complete("Đã tách trang " + minPage + "-" + maxPage + 
                        " thành file: " + outputFiles.get(0).getName());

            } catch (Exception e) {
                logger.error("Error splitting PDF", e);
                result.complete("Lỗi khi tách file: " + e.getMessage());
            }
        });

        return result;
    }

    private CompletableFuture<String> executeDelete(AICommand command, PDFDocument doc) {
        List<Integer> pages = command.getPages();
        if (pages.isEmpty()) {
            return CompletableFuture.completedFuture("Vui lòng chỉ định các trang cần xóa.");
        }

        int totalPages = doc.getTotalPages();
        
        // Validate
        for (int page : pages) {
            if (page < 1 || page > totalPages) {
                return CompletableFuture.completedFuture(
                        "Trang " + page + " không hợp lệ.");
            }
        }

        if (pages.size() >= totalPages) {
            return CompletableFuture.completedFuture(
                    "Không thể xóa tất cả các trang.");
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
            statusCallback.accept("Đã xóa " + pages.size() + " trang");

            return CompletableFuture.completedFuture(
                    "Đã xóa " + pages.size() + " trang: " + pages);

        } catch (Exception e) {
            logger.error("Error deleting pages", e);
            return CompletableFuture.completedFuture("Lỗi khi xóa trang: " + e.getMessage());
        }
    }

    private CompletableFuture<String> executeReorder(AICommand command, PDFDocument doc) {
        List<Integer> newOrder = command.getNewOrder();
        if (newOrder.isEmpty()) {
            return CompletableFuture.completedFuture("Vui lòng chỉ định thứ tự mới.");
        }

        int totalPages = doc.getTotalPages();
        
        if (newOrder.size() != totalPages) {
            return CompletableFuture.completedFuture(
                    "Số trang trong thứ tự mới (" + newOrder.size() + 
                    ") không khớp với tổng số trang (" + totalPages + ").");
        }

        // Convert to 0-based
        List<Integer> zeroBasedOrder = newOrder.stream()
                .map(p -> p - 1)
                .toList();

        try {
            reorderService.reorderPages(doc, zeroBasedOrder);
            Platform.runLater(refreshCallback);
            statusCallback.accept("Đã sắp xếp lại trang");

            return CompletableFuture.completedFuture(
                    "Đã sắp xếp lại trang theo thứ tự: " + newOrder);

        } catch (Exception e) {
            logger.error("Error reordering pages", e);
            return CompletableFuture.completedFuture("Lỗi khi sắp xếp: " + e.getMessage());
        }
    }

    private CompletableFuture<String> executeSwap(AICommand command, PDFDocument doc) {
        int page1 = command.getPage1();
        int page2 = command.getPage2();

        if (page1 == -1 || page2 == -1) {
            return CompletableFuture.completedFuture("Vui lòng chỉ định 2 trang cần hoán đổi.");
        }

        int totalPages = doc.getTotalPages();
        if (page1 < 1 || page1 > totalPages || page2 < 1 || page2 > totalPages) {
            return CompletableFuture.completedFuture(
                    "Trang không hợp lệ. Document có " + totalPages + " trang.");
        }

        if (page1 == page2) {
            return CompletableFuture.completedFuture("Hai trang phải khác nhau.");
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
            statusCallback.accept("Đã hoán đổi trang " + page1 + " và " + page2);

            return CompletableFuture.completedFuture(
                    "Đã hoán đổi trang " + page1 + " và trang " + page2);

        } catch (Exception e) {
            logger.error("Error swapping pages", e);
            return CompletableFuture.completedFuture("Lỗi khi hoán đổi trang: " + e.getMessage());
        }
    }

    private CompletableFuture<String> executeMove(AICommand command, PDFDocument doc) {
        int fromPage = command.getFromPage();
        int toPage = command.getToPage();

        if (fromPage == -1 || toPage == -1) {
            return CompletableFuture.completedFuture("Vui lòng chỉ định trang nguồn và vị trí đích.");
        }

        int totalPages = doc.getTotalPages();
        if (fromPage < 1 || fromPage > totalPages || toPage < 1 || toPage > totalPages) {
            return CompletableFuture.completedFuture(
                    "Trang không hợp lệ. Document có " + totalPages + " trang.");
        }

        if (fromPage == toPage) {
            return CompletableFuture.completedFuture("Trang nguồn và đích phải khác nhau.");
        }

        try {
            reorderService.movePage(doc, fromPage - 1, toPage - 1);
            Platform.runLater(refreshCallback);
            statusCallback.accept("Đã di chuyển trang " + fromPage + " đến vị trí " + toPage);

            return CompletableFuture.completedFuture(
                    "Đã di chuyển trang " + fromPage + " đến vị trí " + toPage);

        } catch (Exception e) {
            logger.error("Error moving page", e);
            return CompletableFuture.completedFuture("Lỗi khi di chuyển trang: " + e.getMessage());
        }
    }

    private CompletableFuture<String> executeBookmark(AICommand command, PDFDocument doc) {
        String title = command.getTitle();
        if (title == null || title.isEmpty()) {
            return CompletableFuture.completedFuture("Vui lòng chỉ định tiêu đề bookmark.");
        }

        int page = command.getPage();
        if (page == -1) {
            page = doc.getCurrentPage() + 1; // Use current page (convert to 1-based for display)
        }

        BookmarkManager bookmarkManager = bookmarkManagerSupplier.get();
        if (bookmarkManager == null) {
            return CompletableFuture.completedFuture("Bookmark manager không khả dụng.");
        }

        int finalPage = page;
        bookmarkManager.addBookmark(finalPage - 1, title); // Convert to 0-based
        statusCallback.accept("Đã thêm bookmark");

        return CompletableFuture.completedFuture(
                "Đã thêm bookmark '" + title + "' tại trang " + finalPage);
    }

    private CompletableFuture<String> executeGoto(AICommand command, PDFDocument doc) {
        int page = command.getPage();
        if (page < 1 || page > doc.getTotalPages()) {
            return CompletableFuture.completedFuture(
                    "Trang " + page + " không hợp lệ. Document có " + doc.getTotalPages() + " trang.");
        }

        Platform.runLater(() -> navigateToPage.accept(page - 1)); // Convert to 0-based
        return CompletableFuture.completedFuture("Đã chuyển đến trang " + page);
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
                        result.append("--- Trang ").append(page).append(" ---\n");
                    }
                    // Limit text length to avoid too long response
                    String trimmedText = text.trim();
                    if (trimmedText.length() > 1000) {
                        trimmedText = trimmedText.substring(0, 1000) + "...\n(Nội dung đã được cắt ngắn)";
                    }
                    result.append(trimmedText).append("\n\n");
                }
            }

            if (result.isEmpty()) {
                return CompletableFuture.completedFuture("Không tìm thấy text trong các trang được chọn.");
            }

            return CompletableFuture.completedFuture(result.toString().trim());

        } catch (Exception e) {
            logger.error("Error reading text from PDF", e);
            return CompletableFuture.completedFuture("Lỗi khi đọc text: " + e.getMessage());
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
                return CompletableFuture.completedFuture("Không tìm thấy text để tóm tắt.");
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
            
            return CompletableFuture.completedFuture("Đã đọc nội dung nhưng không thể tóm tắt.");

        } catch (Exception e) {
            logger.error("Error summarizing PDF", e);
            return CompletableFuture.completedFuture("Lỗi khi tóm tắt: " + e.getMessage());
        }
    }

    private CompletableFuture<String> executeInfo(PDFDocument doc) {
        StringBuilder info = new StringBuilder();
        info.append("Thông tin document:\n");
        info.append("- File: ").append(doc.getFile() != null ? doc.getFile().getName() : "Chưa lưu").append("\n");
        info.append("- Tổng số trang: ").append(doc.getTotalPages()).append("\n");
        info.append("- Trang hiện tại: ").append(doc.getCurrentPage() + 1).append("\n");
        
        return CompletableFuture.completedFuture(info.toString());
    }

    private CompletableFuture<String> executeHelp() {
        String help = """
                Các lệnh có thể sử dụng:
                
                - Tách trang: "Tách trang 1-5", "Extract page 1,3,5"
                - Xóa trang: "Xóa trang 3", "Delete page 2,4,6"
                - Hoán đổi: "Đổi vị trí trang 1 với trang 2"
                - Di chuyển: "Di chuyển trang 5 đến vị trí 2"
                - Bookmark: "Đánh dấu trang này là Chương 1"
                - Đi đến: "Đi đến trang 10", "Go to page 5"
                - Đọc text: "Đọc nội dung trang 1"
                - Tóm tắt: "Tóm tắt trang này", "Tóm tắt 3 trang đầu"
                - Thông tin: "Thông tin file", "Document info"
                
                Bạn có thể dùng tiếng Việt hoặc tiếng Anh!
                """;
        return CompletableFuture.completedFuture(help);
    }
}
