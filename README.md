# PDF Lite

A lightweight, modern PDF viewer and editor built with JavaFX. PDF Lite provides comprehensive PDF viewing and editing capabilities with a clean, intuitive interface.

## Features

### Viewing
- Smooth scrolling and navigation
- Multiple zoom levels with fit-to-width/page options
- Page navigation (first, previous, next, last, go to page)
- Search with result highlighting
- Text selection and copying
- Fullscreen mode
- Dark and light theme support
- Multi-tab support for opening multiple PDFs
- Bookmarks management

### Editing
- Page reordering via drag-and-drop
- Page deletion and duplication
- PDF merging (combine multiple PDFs)
- PDF splitting (extract pages or ranges)
- Insert pages from other PDFs
- Add watermarks (text/image)
- Image insertion into PDF pages
- Text editing (basic)

### Annotations
- Highlight text
- Draw shapes (rectangle, circle, arrow)
- Add comments
- Annotation persistence

### Export & Print
- Export pages as images (PNG, JPEG)
- Print with custom settings
- PDF compression/optimization

### AI Features (Optional)
- AI-powered chat assistant for PDF content
- Summarize documents
- Extract and analyze text
- Powered by Groq API

To enable AI features, create `.pdflite/ai-config.json` with your Groq API key:

```json
{
  "groqApiKey": "your-api-key-here",
  "model": "llama-3.3-70b-versatile",
  "fastModel": "llama-3.1-8b-instant",
  "enabled": true
}
```

Get your API key at: https://console.groq.com/keys

### Other
- Image extraction from PDFs
- Document metadata viewing/editing
- PDF encryption/decryption
- Auto-save and recovery
- Recent files tracking
- Keyboard shortcuts

## Requirements

- Java 21 or higher
- Maven 3.6+ (or use included Maven wrapper)

## Building from Source

```bash
git clone https://github.com/phamngocpho/pdf-lite.git
cd pdf-lite
./mvnw clean package
```

## Running

```bash
./mvnw javafx:run
```

Or run the JAR:
```bash
java -jar target/pdf-lite-1.0-SNAPSHOT.jar
```

## Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Open file | Ctrl+O |
| Save | Ctrl+S |
| Save As | Ctrl+Shift+S |
| Print | Ctrl+P |
| Search | Ctrl+F |
| Go to page | Ctrl+G |
| Zoom in | Ctrl++ |
| Zoom out | Ctrl+- |
| Fit width | Ctrl+0 |
| Fit page | Ctrl+1 |
| First page | Home |
| Last page | End |
| Previous page | Page Up |
| Next page | Page Down |
| Fullscreen | F11 |
| Toggle theme | Ctrl+T |
| Undo | Ctrl+Z |
| Redo | Ctrl+Y |

## Dependencies

- JavaFX 21.0.6 - UI framework
- Apache PDFBox 3.0.3 - PDF processing
- Gson 2.10.1 - JSON serialization
- SLF4J 2.0.9 + Logback 1.5.20 - Logging

## Project Structure

```
src/main/java/org/pdflite/
├── command/        # Command pattern for undo/redo
├── config/         # Configuration (AI settings)
├── controller/     # UI controllers
├── dialog/         # Custom dialogs
├── manager/        # Business logic managers
├── model/          # Data models and annotations
├── service/        # PDF and AI services
├── util/           # Utility classes
└── view/           # Custom UI components

src/main/resources/org/pdflite/
├── *.fxml          # UI layouts
├── *.css           # Themes and styles
└── images/         # Icons and images
```

## Contributing

Contributions are welcome! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## Privacy Notice

AI features use the Groq API. When enabled, PDF content may be sent to Groq's servers. Review their privacy policy at: https://groq.com/privacy-policy

You can use PDF Lite without AI features if you prefer not to share data.

## Disclaimer

This software is provided "AS IS" without warranty of any kind. The authors are not responsible for any data loss, file corruption, or other damages.

**Always backup your PDF files before editing operations.**

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.

## Acknowledgments

- Apache PDFBox team
- JavaFX community
- All contributors
