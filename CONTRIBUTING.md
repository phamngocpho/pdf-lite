# Contributing to PDF Lite

Thank you for considering contributing to PDF Lite! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and collaborative environment for everyone.

## License

By contributing to PDF Lite, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

## How to Contribute

### Reporting Bugs

If you find a bug, please create an issue with the following information:

- A clear, descriptive title
- Steps to reproduce the issue
- Expected behavior
- Actual behavior
- Your environment (OS, Java version, etc.)
- Screenshots if applicable

### Suggesting Features

Feature suggestions are welcome! Please create an issue with:

- A clear description of the feature
- Use cases and benefits
- Any relevant examples or mockups

### Pull Requests

1. Fork the repository
2. Create a new branch for your feature or fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```
   or
   ```bash
   git checkout -b fix/your-bug-fix
   ```

3. Make your changes following the coding standards below

4. Test your changes thoroughly

5. Commit your changes with clear, descriptive messages:
   ```bash
   git commit -m "Add feature: description of what you added"
   ```

6. Push to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```

7. Create a Pull Request with:
   - Clear title and description
   - Reference to any related issues
   - Screenshots or examples if applicable

## Development Setup

### Prerequisites

- Java 21 or higher
- Maven 3.6+ (or use the included Maven wrapper)
- Git

### Building the Project

```bash
git clone https://github.com/phamngocpho/pdf-lite.git
cd pdf-lite
./mvnw clean install
```

### Running Tests

```bash
./mvnw test
```

### Running the Application

```bash
./mvnw javafx:run
```

## Coding Standards

### Java Style Guide

- Use four spaces for indentation (no tabs)
- Follow standard Java naming conventions:
  - Classes: PascalCase
  - Methods and variables: camelCase
  - Constants: UPPER_SNAKE_CASE
- Maximum line length: 120 characters
- Always use braces for control structures

### Documentation

- Add Javadoc comments for all public classes and methods
- Include parameter descriptions and return value documentation
- Document any exceptions that may be thrown
- Keep comments up to date with code changes

Example:

```java
/**
 * Loads a PDF document from the specified file.
 *
 * @param file the PDF file to load
 * @return the loaded PDFDocument object
 */
public PDFDocument loadDocument(File file) {
    // implementation
}
```

### Code Organization

- Keep classes focused on a single responsibility
- Use meaningful names for classes, methods, and variables
- Avoid deep nesting (max 3–4 levels)
- Extract complex logic into separate methods
- Use appropriate design patterns where applicable

### Error Handling

- Use appropriate exception types
- Log errors with meaningful messages
- Provide user-friendly error messages in the UI
- Clean up resources properly (use try-with-resources)

### Testing

- Write unit tests for new functionality
- Maintain or improve code coverage
- Test edge cases and error conditions
- Use descriptive test method names

## Project Structure

```
src/main/java/org/pdflite/
├── command/         # Command pattern for undo/redo
├── config/          # Configuration (AI settings)
├── controller/      # JavaFX controllers for UI
├── dialog/          # Custom dialog windows
├── manager/         # Business logic and state management
├── model/           # Data models and entities
├── service/         # PDF processing and other services
├── util/            # Utility classes and helpers
└── view/            # Custom UI components
```

## Commit Message Guidelines

This project uses a simple [Conventional Commits](https://www.conventionalcommits.org/) format.

### Format

```
<type>: <description>
```

For multiple changes in one commit, use semicolons:
```
<type>: <change1>; <change2>; <change3>
```

### Types

- `feat`: A new feature
- `fix`: A bug fix
- `refactor`: Code change that neither fixes a bug nor adds a feature
- `chore`: Maintenance tasks, workflow updates, dependency updates
- `docs`: Documentation only changes
- `style`: Code style changes (formatting, missing semicolons, etc.)
- `perf`: Performance improvements
- `test`: Adding or updating tests

### Examples from this project

```
feat: add AI assistant chat sidebar with Groq integration for PDF operations
feat: add privacy consent dialog for AI features; update license to Apache 2.0; improve theme and config handling
feat: add PDF merge and split functionality with UI integration
feat: dark and light theme toggle
fix: adjust base font size to 13px in light and dark themes; clean up image extraction log output
fix: improve zoom functionality and maintain scroll position
refactor: extract MainController into manager classes
chore: auto move assigned issues to in progress
```

## Review Process

All submissions require review before merging:

1. Code must follow the style guidelines
2. All tests must pass
3. New features should include tests
4. Documentation must be updated if needed
5. At least one maintainer must approve the changes

## Questions?

If you have questions about contributing, feel free to:

- Open an issue for discussion
- Reach out to the maintainers
- Check existing issues and pull requests for similar topics

Thank you for contributing to PDF Lite!

