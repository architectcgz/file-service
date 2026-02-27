package com.architectcgz.file.infrastructure.cache;

/**
 * 分片上传 Redis Key 定义
 * 
 * 命名规范：upload:task:{taskId}:{entity}
 * 
 * Bitmap 结构说明：
 * - Key: upload:task:{taskId}:parts
 * - Type: Bitmap
 * - TTL: 24 小时（可配置）
 * - Bit 位置 = partNumber - 1
 * - Bit 值: 0=未上传, 1=已上传
 * 
 * 示例：
 * - 分片 1 对应 bit 0
 * - 分片 2 对应 bit 1
 * - 分片 1000 对应 bit 999
 * 
 * 存储效率：
 * - 1000 个分片仅需 125 字节 (1000 bits / 8)
 * - 10000 个分片仅需 1.25 KB
 *
 * @author File Service Team
 */
public final class UploadRedisKeys {

    private static final String PREFIX = "upload:task";
    private static final String PARTS_SUFFIX = "parts";

    private UploadRedisKeys() {
        // 工具类，禁止实例化
    }

    // ==================== 分片上传 Bitmap ====================

    /**
     * 分片状态 Bitmap
     * 
     * Key: upload:task:{taskId}:parts
     * Type: Bitmap
     * TTL: 24 小时（可配置）
     * 
     * 用途：
     * - 记录分片上传状态（SETBIT）
     * - 查询已完成分片数量（BITCOUNT）
     * - 查询特定分片状态（GETBIT）
     * 
     * @param taskId 上传任务ID，不能为 null 或空字符串
     * @return Redis key，格式为 upload:task:{taskId}:parts
     * @throws IllegalArgumentException 如果 taskId 为 null 或空字符串
     */
    public static String partsBitmap(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            throw new IllegalArgumentException("taskId 不能为 null 或空字符串");
        }
        return PREFIX + ":" + taskId + ":" + PARTS_SUFFIX;
    }

    /**
     * 计算分片编号对应的 Bitmap 位偏移量
     * 
     * 分片编号从 1 开始，Bitmap 位偏移量从 0 开始
     * 因此：bitOffset = partNumber - 1
     * 
     * @param partNumber 分片编号，必须 >= 1
     * @return Bitmap 位偏移量
     * @throws IllegalArgumentException 如果 partNumber < 1
     */
    public static long getBitOffset(int partNumber) {
        if (partNumber < 1) {
            throw new IllegalArgumentException("分片编号必须 >= 1，当前值: " + partNumber);
        }
        return partNumber - 1L;
    }

    /**
     * 根据 Bitmap 位偏移量计算分片编号
     * 
     * Bitmap 位偏移量从 0 开始，分片编号从 1 开始
     * 因此：partNumber = bitOffset + 1
     * 
     * @param bitOffset Bitmap 位偏移量，必须 >= 0
     * @return 分片编号
     * @throws IllegalArgumentException 如果 bitOffset < 0
     */
    public static int getPartNumber(long bitOffset) {
        if (bitOffset < 0) {
            throw new IllegalArgumentException("Bitmap 位偏移量必须 >= 0，当前值: " + bitOffset);
        }
        return (int) (bitOffset + 1);
    }
}
