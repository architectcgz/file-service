namespace FileService.Constants;

public sealed class BusinessError
{
    // 禁止外部实例化
    private BusinessError(int code, string message)
    {
        Code = code;
        Message = message;
    }

    public int Code { get; }
    public string Message { get; }

    // 系统错误
    public static readonly BusinessError InternalError = new(500, "内部错误");
    
    //上传文件相关错误
    public static BusinessError FileIsNull = new(2019, "上传的文件为空");
    public static BusinessError UploadError = new(2020, "上传失败");
}

