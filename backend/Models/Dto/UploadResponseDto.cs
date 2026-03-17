namespace FileService.Models.Dto;

public class UploadResponseDto
{
    public string Url { get; set; } = string.Empty;
    public string Key { get; set; } = string.Empty;
    public bool Success { get; set; }
    public string Message { get; set; } = string.Empty;
}

