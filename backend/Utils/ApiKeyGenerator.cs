using System.Security.Cryptography;
using System.Text;

namespace FileService.Utils;

/// <summary>
/// API Key生成工具
/// 用于生成安全的随机API Key
/// </summary>
public static class ApiKeyGenerator
{
    /// <summary>
    /// 生成一个安全的随机API Key
    /// </summary>
    /// <param name="length">Key长度，默认64字符</param>
    /// <returns>Base64编码的随机字符串</returns>
    public static string GenerateApiKey(int length = 64)
    {
        // 使用加密安全的随机数生成器
        using var rng = RandomNumberGenerator.Create();
        var bytes = new byte[length];
        rng.GetBytes(bytes);
        
        // 转换为Base64字符串，然后截取到指定长度
        var base64 = Convert.ToBase64String(bytes);
        
        // 移除Base64中的特殊字符，只保留字母和数字
        var cleanKey = new StringBuilder();
        foreach (var c in base64)
        {
            if (char.IsLetterOrDigit(c))
            {
                cleanKey.Append(c);
                if (cleanKey.Length >= length)
                {
                    break;
                }
            }
        }
        
        // 如果长度不够，继续填充
        while (cleanKey.Length < length)
        {
            var extraBytes = new byte[length];
            rng.GetBytes(extraBytes);
            foreach (var b in extraBytes)
            {
                if (cleanKey.Length >= length) break;
                var c = (char)('A' + (b % 26));
                cleanKey.Append(c);
            }
        }
        
        return cleanKey.ToString().Substring(0, length);
    }

    /// <summary>
    /// 生成一个包含特殊字符的API Key（更安全，但需要URL编码）
    /// </summary>
    /// <param name="length">Key长度，默认64字符</param>
    /// <returns>Base64编码的随机字符串</returns>
    public static string GenerateApiKeyWithSpecialChars(int length = 64)
    {
        using var rng = RandomNumberGenerator.Create();
        var bytes = new byte[length];
        rng.GetBytes(bytes);
        
        // 转换为Base64字符串
        var base64 = Convert.ToBase64String(bytes);
        
        // 截取到指定长度
        return base64.Substring(0, Math.Min(length, base64.Length));
    }

    /// <summary>
    /// 生成一个简单的字母数字API Key（易于使用）
    /// </summary>
    /// <param name="length">Key长度，默认32字符</param>
    /// <returns>只包含字母和数字的随机字符串</returns>
    public static string GenerateSimpleApiKey(int length = 32)
    {
        const string chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        using var rng = RandomNumberGenerator.Create();
        var bytes = new byte[length];
        rng.GetBytes(bytes);
        
        var result = new StringBuilder(length);
        foreach (var b in bytes)
        {
            result.Append(chars[b % chars.Length]);
        }
        
        return result.ToString();
    }
}

