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

- Use 4 spaces for indentation (no tabs)
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
 * @throws IOException if the file cannot be read
 */
public PDFDocument loadDocument(File file) throws IOException {
    // implementation
}
```

### Code Organization

- Keep classes focused on a single responsibility
- Use meaningful names for classes, methods, and variables
- Avoid deep nesting (max 3-4 levels)
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
├── controller/      # JavaFX controllers for UI
├── manager/         # Business logic and state management
├── model/           # Data models and entities
├── service/         # PDF processing and other services
├── util/            # Utility classes and helpers
└── view/            # Custom UI components
```

## Commit Message Guidelines

This project follows the [Conventional Commits](https://www.conventionalcommits.org/) specification.

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
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

### Scope (optional)

Scope is typically omitted unless needed for clarity. When used, it should specify the affected area.

### Examples from this project

```
feat: add recent files, session restore, and improve search documentation
feat: add PDF merge and split functionality with UI integration
feat: dark and light theme toggle
feat: extract/Copy single image
feat: text extract(copy)
refactor: streamline text extraction and logging in ContextMenuHandler
refactor: update SelectionInfo class to use records and streamline text access
refactor: convert classes to records and clean up code
refactor: extract MainController into manager classes
fix: resolve merge conflicts and refactor methods into managers
fix: improve zoom functionality and maintain scroll position
fix: change token secret from PROJECT_TOKEN to GH_TOKEN
chore: auto move assigned issues to in progress
chore: change GITHUB_TOKEN to GH_TOKEN in workflow
```

### Breaking Changes

For breaking changes that affect backward compatibility, append `!` after the type:

```
feat!: change PDF loading method signature

BREAKING CHANGE: loadPDF now requires File parameter instead of String path
```

Note: Breaking changes are rare in this project since it's an end-user application.

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

