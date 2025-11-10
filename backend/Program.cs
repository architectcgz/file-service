using System.Text.Json.Serialization;
using FileService.Config;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Diagnostics;
using Scalar.AspNetCore;
using FileService.Repositories;
using FileService.Services.Impl;
using FileService.Services.Interfaces;
using FileService.Utils;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddLogging(logging =>
{
    logging.AddConsole();
    logging.AddDebug();
});

builder.Services.AddControllers()
    .AddJsonOptions(options =>
    {
        options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter());
        options.JsonSerializerOptions.PropertyNamingPolicy = System.Text.Json.JsonNamingPolicy.CamelCase;
    })
    .ConfigureApiBehaviorOptions(options =>
    {
        options.InvalidModelStateResponseFactory = context =>
        {
            var firstError = context.ModelState
                .Where(e => e.Value?.Errors.Count > 0)
                .Select(e => e.Value!.Errors.First().ErrorMessage)
                .FirstOrDefault();
            return new BadRequestObjectResult(new { message = firstError });
        };
    });

// 数据库配置
builder.Services.Configure<DatabaseConfig>(builder.Configuration.GetSection("Database"));

// RustFS 配置
builder.Services.Configure<FileService.Config.RustFSConfig>(
    builder.Configuration.GetSection("RustFSConfig"));

// 文件服务安全配置
builder.Services.Configure<FileService.Config.FileServiceSecurityConfig>(
    builder.Configuration.GetSection("FileServiceSecurity"));

// 准备数据库连接字符串
var dbConfig = builder.Configuration.GetSection("Database").Get<DatabaseConfig>() ?? new DatabaseConfig();
var dbBaseConnectionString = builder.Configuration.GetConnectionString("PostgresSql")!;
// 简化处理：直接使用连接字符串，如果需要环境变量支持可以后续添加
var dbOptimizedConnectionString = dbConfig.BuildConnectionString(dbBaseConnectionString);

// 注册 DbContext
builder.Services.AddDbContext<FileServiceDbContext>(options =>
{
    options.UseNpgsql(dbOptimizedConnectionString, npgsqlOptions => { })
        .UseSnakeCaseNamingConvention()
        .EnableSensitiveDataLogging(false)
        .EnableDetailedErrors(builder.Environment.IsDevelopment())
        .ConfigureWarnings(warnings =>
        {
            warnings.Log(RelationalEventId.MultipleCollectionIncludeWarning);
        });
});

// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();

// 内存缓存（用于RustFSUtil的本地缓存，必须在RustFSUtil之前注册）
builder.Services.AddMemoryCache();

// 注册服务
builder.Services.AddScoped<IUploadService, UploadService>();

// RustFSUtil 需要 IConfiguration 和 IMemoryCache（已注册）
builder.Services.AddSingleton<RustFSUtil>();

var app = builder.Build();

// Configure the HTTP request pipeline.

// 转发头中间件（必须在其他中间件之前）
app.UseForwardedHeaders(new ForwardedHeadersOptions
{
    ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto
});

// 路由中间件
app.UseRouting();

// 开发环境配置
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
    app.MapScalarApiReference();
}

app.MapControllers();

AppContext.SetSwitch("Npgsql.EnableLegacyTimestampBehavior", true);
await app.RunAsync();

