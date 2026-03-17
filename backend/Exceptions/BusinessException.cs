using FileService.Constants;

namespace FileService.Exceptions;

public class BusinessException(int code, string errorMessage) : Exception(errorMessage)
{
    public int Code { get; set; } = code;
    public string ErrorMessage { get; set; } = errorMessage;

    public BusinessException(BusinessError businessError) 
        : this(businessError.Code, businessError.Message)
    {
    }
}

