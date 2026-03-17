// 这是一个简单的控制台程序，用于生成API Key
// 使用方法：在项目根目录运行： dotnet run --project backend/FileService.csproj -- generate-api-key

using FileService.Utils;

namespace FileService.Utils;

/// <summary>
/// API Key生成命令行工具
/// 运行方式：dotnet run -- generate-api-key
/// </summary>
public class GenerateApiKeyProgram
{
    public static void Run(string[] args)
    {
        if (args.Length > 0 && args[0] == "generate-api-key")
        {
            Console.WriteLine("=== 管理员API Key生成工具 ===\n");
            
            Console.WriteLine("请选择生成方式：");
            Console.WriteLine("1. 简单API Key（32字符，只包含字母和数字，推荐）");
            Console.WriteLine("2. 标准API Key（64字符，只包含字母和数字）");
            Console.WriteLine("3. 增强API Key（64字符，包含特殊字符，需要URL编码）");
            Console.Write("\n请输入选项 (1-3，默认1): ");
            
            var choice = Console.ReadLine()?.Trim() ?? "1";
            
            string apiKey;
            switch (choice)
            {
                case "2":
                    apiKey = ApiKeyGenerator.GenerateApiKey(64);
                    break;
                case "3":
                    apiKey = ApiKeyGenerator.GenerateApiKeyWithSpecialChars(64);
                    break;
                default:
                    apiKey = ApiKeyGenerator.GenerateSimpleApiKey(32);
                    break;
            }
            
            Console.WriteLine("\n=== 生成的API Key ===");
            Console.WriteLine(apiKey);
            Console.WriteLine("\n=== 配置说明 ===");
            Console.WriteLine("请将以下内容添加到 appsettings.json 或 appsettings.Production.json:");
            Console.WriteLine($"\"AdminApiKey\": \"{apiKey}\"");
            Console.WriteLine("\n=== 使用说明 ===");
            Console.WriteLine("调用管理员接口时，请在请求头中添加：");
            Console.WriteLine($"X-Admin-Api-Key: {apiKey}");
            Console.WriteLine("\n请妥善保管此API Key，不要泄露给他人！");
        }
    }
}

