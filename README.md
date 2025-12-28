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

To enable AI features, add your Groq API key in Settings (File > Settings > Files section) or edit `.pdflite/preferences.json`:

```json
{
  "aiApiKey": "your-api-key-here",
  "aiModel": "llama-3.3-70b-versatile",
  "aiFastModel": "llama-3.1-8b-instant",
  "aiEnabled": true
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
- Centralized settings panel (File > Settings)

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
| Export | Ctrl+Shift+E |
| Optimize PDF | Ctrl+Shift+O |
| Document Properties | Ctrl+D |
| Settings | Ctrl+Alt+S |
| Exit | Alt+F4 |
| Edit Text | Ctrl+E |
| Insert Image | Ctrl+I |
| Insert Stamp | Ctrl+Shift+I |
| Search | Ctrl+F |
| Hide Search | Escape |
| Zoom in | Ctrl++ |
| Zoom out | Ctrl+- |
| Fit width | Ctrl+0 |
| Fit page | Ctrl+1 |
| Toggle bookmarks | Ctrl+B |
| Add bookmark | Ctrl+Shift+B |
| Smart Bookmarks | Ctrl+Alt+B |
| Import Outline | Ctrl+Alt+O |
| Clear Bookmarks | Ctrl+Shift+Delete |
| Toggle Toolbar | Ctrl+T |
| Fullscreen | F11 |
| Highlight | Ctrl+H |
| Add Watermark | Ctrl+W |
| Merge PDFs | Ctrl+M |
| Split PDF | Ctrl+Shift+P |
| Extract Pages | Ctrl+Shift+X |
| Reorder Pages | Ctrl+Shift+R |
| Delete page | Delete |
| Duplicate page | Ctrl+Shift+D |
| Undo | Ctrl+Z |
| Redo | Ctrl+Y |
| AI Assistant | Ctrl+Shift+A |
| About | F1 |

## Dependencies

- JavaFX 21.0.6 - UI framework
- Apache PDFBox 3.0.3 - PDF processing
- Gson 2.10.1 - JSON serialization
- SLF4J 2.0.9 + Logback 1.5.20 - Logging

## Configuration

All settings are stored in `.pdflite/preferences.json`:

```json
{
  "defaultZoom": 1.0,
  "fitMode": "none",
  "themeMode": "SYSTEM",
  "language": "en",
  "sidebarPosition": "left",
  "autoSaveEnabled": true,
  "autoSaveDelaySeconds": 5,
  "maxRecentFiles": 10,
  "aiApiKey": "",
  "aiModel": "llama-3.3-70b-versatile",
  "aiEnabled": true
}
```

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
