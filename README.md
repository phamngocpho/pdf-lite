# PDF Lite

A lightweight, modern PDF viewer and editor built with JavaFX. PDF Lite provides essential PDF viewing capabilities with a clean, intuitive interface.

## Features

- PDF viewing with smooth scrolling and navigation
- Zoom controls with multiple zoom levels
- Page navigation (first, previous, next, last page)
- Search functionality with result highlighting
- Text selection and copying
- Annotation support with highlighting
- Image extraction from PDF documents
- PDF merging and splitting capabilities
- Recent files tracking
- Dark and light theme support
- Fullscreen mode
- Keyboard shortcuts for common operations

## Requirements

- Java 21 or higher
- Maven 3.6 or higher (or use included Maven wrapper)

## Building from Source

Clone the repository and build using Maven wrapper:

```bash
git clone https://github.com/yourusername/pdf-lite.git
cd pdf-lite
./mvnw clean package
```

On Windows:

```cmd
mvnw.cmd clean package
```

## Running the Application

After building, run the application using:

```bash
./mvnw javafx:run
```

Or run the generated JAR file:

```bash
java -jar target/pdf-lite-1.0-SNAPSHOT.jar
```

## Usage

### Opening PDF Files

- Click "Open" button or use Ctrl+O
- Drag and drop PDF files onto the application window
- Recent files are accessible from the File menu

### Navigation

- Use navigation buttons or keyboard shortcuts:
  - Home: Go to first page
  - End: Go to last page
  - Page Up/Down: Navigate pages
  - Ctrl+G: Go to specific page

### Zoom Controls

- Zoom in: Ctrl++ or Ctrl+Scroll Up
- Zoom out: Ctrl+- or Ctrl+Scroll Down
- Fit width: Ctrl+0
- Fit page: Ctrl+1

### Search

- Open search: Ctrl+F
- Find next: F3 or Enter
- Find previous: Shift+F3

### Other Features

- Fullscreen: F11
- Toggle theme: Ctrl+T
- Extract images: Available in context menu
- Merge PDFs: File > Merge PDFs
- Split PDF: File > Split PDF

## Dependencies

- JavaFX 21.0.6 - UI framework
- Apache PDFBox 3.0.3 - PDF processing
- SLF4J 2.0.9 - Logging facade
- Logback 1.5.20 - Logging implementation

## Project Structure

```
pdf-lite/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/pdflite/
│       │       ├── controller/     # UI controllers
│       │       ├── manager/        # Business logic managers
│       │       ├── model/          # Data models
│       │       ├── service/        # PDF processing services
│       │       ├── util/           # Utility classes
│       │       └── view/           # Custom UI components
│       └── resources/
│           └── org/pdflite/
│               ├── *.fxml          # UI layouts
│               └── *.css           # Stylesheets
├── pom.xml                         # Maven configuration
└── LICENSE                         # MIT License

```

## Development

### Code Style

- Follow standard Java naming conventions
- Use meaningful variable and method names
- Add Javadoc comments for public APIs
- Keep methods focused and concise

### Building Documentation

Generate Javadoc documentation:

```bash
./mvnw javadoc:javadoc
```

Documentation will be available in `target/site/apidocs/`

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

See [CONTRIBUTING.md](CONTRIBUTING.md) for more details.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- Apache PDFBox team for the excellent PDF library
- JavaFX community for the UI framework
- All contributors who have helped improve this project

