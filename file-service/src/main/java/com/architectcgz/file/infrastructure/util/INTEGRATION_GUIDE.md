# FileTypeDetector Integration Guide

## Overview

`FileTypeDetector` is a utility class that detects actual file types by reading magic numbers (file signatures) from file headers. This prevents file type spoofing where users rename malicious files with safe extensions.

## Usage

### Basic Detection

```java
// Read first 12 bytes of file
byte[] fileHeader = new byte[12];
inputStream.read(fileHeader);

// Detect file type
String detectedType = FileTypeDetector.detectFileType(fileHeader);
// Returns: "image/jpeg", "image/png", "application/pdf", etc.
```

### Integration with FileTypeValidator

```java
@Service
public class FileTypeValidator {
    
    public void validateFileWithMagicNumber(String fileName, String contentType, 
                                           byte[] fileHeader, long fileSize) {
        // 1. Validate extension
        validateFileExtension(fileName);
        
        // 2. Validate Content-Type
        validateContentType(contentType);
        
        // 3. Validate file size
        validateFileSize(fileSize);
        
        // 4. Detect actual file type from magic number
        String detectedType = FileTypeDetector.detectFileType(fileHeader);
        if (detectedType == null) {
            throw new BusinessException("无法识别文件类型");
        }
        
        // 5. Verify declared type matches detected type
        if (!FileTypeDetector.isTypeMatch(contentType, detectedType)) {
            throw new BusinessException("文件类型与内容不匹配");
        }
    }
}
```

### In Upload Controller

```java
@PostMapping("/upload")
public ApiResponse<String> uploadFile(@RequestParam("file") MultipartFile file) {
    // Read first 12 bytes for magic number detection
    byte[] fileHeader = new byte[12];
    try (InputStream is = file.getInputStream()) {
        is.read(fileHeader);
    }
    
    // Validate with magic number detection
    fileTypeValidator.validateFileWithMagicNumber(
        file.getOriginalFilename(),
        file.getContentType(),
        fileHeader,
        file.getSize()
    );
    
    // Proceed with upload...
}
```

## Supported File Types

| File Type | MIME Type | Magic Number (Hex) |
|-----------|-----------|-------------------|
| JPEG | image/jpeg | FF D8 FF |
| PNG | image/png | 89 50 4E 47 0D 0A 1A 0A |
| GIF | image/gif | 47 49 46 38 |
| PDF | application/pdf | 25 50 44 46 |
| WebP | image/webp | 52 49 46 46 xx xx xx xx 57 45 42 50 |
| MP4 | video/mp4 | 00 00 00 xx 66 74 79 70 |
| ZIP | application/zip | 50 4B 03 04 |

## Special Cases

### JPEG Variants
Both `image/jpg` and `image/jpeg` are accepted as valid JPEG types.

### Office Documents
DOCX, XLSX, and PPTX files are ZIP-based formats. The detector will identify them as `application/zip`, which is correct. The `isTypeMatch()` method handles this special case.

### Unknown Types
If a file type cannot be detected, `detectFileType()` returns `null`. Your application should decide whether to reject unknown types or allow them.

## Security Considerations

1. **Always validate magic numbers** for security-sensitive uploads
2. **Read sufficient bytes**: At least 12 bytes recommended for reliable detection
3. **Combine with other validations**: Extension, Content-Type, and file size
4. **Whitelist approach**: Only allow known safe file types

## Performance

- Magic number detection is very fast (microseconds)
- Only reads first 12 bytes of file
- No external dependencies required
- Thread-safe (all methods are static)

## Testing

See `FileTypeDetectorTest.java` for comprehensive test coverage including:
- All supported file types
- Type matching logic
- Edge cases (null, insufficient data, unknown types)
- Fake extension detection
