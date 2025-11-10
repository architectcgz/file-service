using Npgsql;

namespace FileService.Config;

/// <summary>
/// 数据库连接池配置
/// </summary>
public class DatabaseConfig
{
    /// <summary>
    /// 连接池最小连接数
    /// </summary>
    public int MinPoolSize { get; set; } = 5;
    
    /// <summary>
    /// 连接池最大连接数
    /// </summary>
    public int MaxPoolSize { get; set; } = 100;
    
    /// <summary>
    /// 连接超时时间（秒）
    /// </summary>
    public int ConnectionTimeout { get; set; } = 30;
    
    /// <summary>
    /// 命令超时时间（秒）
    /// </summary>
    public int CommandTimeout { get; set; } = 60;
    
    /// <summary>
    /// 连接空闲超时时间（秒）
    /// </summary>
    public int ConnectionIdleLifetime { get; set; } = 300;
    
    /// <summary>
    /// 连接池清理间隔（秒）
    /// </summary>
    public int ConnectionPruningInterval { get; set; } = 10;
    
    /// <summary>
    /// 是否启用连接池
    /// </summary>
    public bool Pooling { get; set; } = true;
    
    /// <summary>
    /// 是否启用连接多路复用
    /// 注意：启用多路复用时必须使用异步方法，不支持同步数据库操作
    /// </summary>
    public bool Multiplexing { get; set; } = true;
    
    /// <summary>
    /// 每个连接的最大多路复用数
    /// </summary>
    public int MaxAutoPrepare { get; set; } = 20;
    
    /// <summary>
    /// 是否启用TCP keep-alive
    /// </summary>
    public bool TcpKeepAlive { get; set; } = true;
    
    /// <summary>
    /// TCP keep-alive 间隔（秒）
    /// </summary>
    public int TcpKeepAliveTime { get; set; } = 600;
    
    /// <summary>
    /// TCP keep-alive 间隔（秒）
    /// </summary>
    public int TcpKeepAliveInterval { get; set; } = 60;
    
    /// <summary>
    /// 构建连接字符串
    /// </summary>
    /// <param name="baseConnectionString">基础连接字符串</param>
    /// <returns>优化后的连接字符串</returns>
    public string BuildConnectionString(string baseConnectionString)
    {
        var builder = new NpgsqlConnectionStringBuilder(baseConnectionString)
        {
            Pooling = Pooling,
            MinPoolSize = MinPoolSize,
            MaxPoolSize = MaxPoolSize,
            Timeout = ConnectionTimeout,
            CommandTimeout = CommandTimeout,
            ConnectionIdleLifetime = ConnectionIdleLifetime,
            ConnectionPruningInterval = ConnectionPruningInterval,
            Multiplexing = Multiplexing,
            MaxAutoPrepare = MaxAutoPrepare,
            TcpKeepAlive = TcpKeepAlive,
            TcpKeepAliveTime = TcpKeepAliveTime,
            TcpKeepAliveInterval = TcpKeepAliveInterval,
            // 优化设置
            ReadBufferSize = 8192,
            WriteBufferSize = 8192,
            SocketReceiveBufferSize = 8192,
            SocketSendBufferSize = 8192,
            NoResetOnClose = true, // 连接关闭时不重置状态，提高性能
        };
        
        return builder.ToString();
    }
}

