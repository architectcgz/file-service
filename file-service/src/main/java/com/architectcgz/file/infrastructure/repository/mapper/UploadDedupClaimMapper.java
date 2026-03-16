package com.architectcgz.file.infrastructure.repository.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

/**
 * 上传去重占位 Mapper。
 */
public interface UploadDedupClaimMapper extends RuntimeMyBatisMapper {

    @Insert("""
        INSERT INTO upload_dedup_claims (
            app_id, file_hash, bucket_name, owner_token, expires_at, created_at, updated_at
        ) VALUES (
            #{appId}, #{fileHash}, #{bucketName}, #{ownerToken},
            #{expiresAt},
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        )
        ON CONFLICT (app_id, file_hash, bucket_name)
        DO UPDATE SET owner_token = EXCLUDED.owner_token,
                      expires_at = EXCLUDED.expires_at,
                      updated_at = CURRENT_TIMESTAMP
        WHERE upload_dedup_claims.expires_at < CURRENT_TIMESTAMP
        """)
    int tryAcquireClaim(@Param("appId") String appId,
                        @Param("fileHash") String fileHash,
                        @Param("bucketName") String bucketName,
                        @Param("ownerToken") String ownerToken,
                        @Param("expiresAt") OffsetDateTime expiresAt);

    @org.apache.ibatis.annotations.Update("""
        UPDATE upload_dedup_claims
        SET expires_at = #{expiresAt},
            updated_at = CURRENT_TIMESTAMP
        WHERE app_id = #{appId}
          AND file_hash = #{fileHash}
          AND bucket_name = #{bucketName}
          AND owner_token = #{ownerToken}
        """)
    int renewClaim(@Param("appId") String appId,
                   @Param("fileHash") String fileHash,
                   @Param("bucketName") String bucketName,
                   @Param("ownerToken") String ownerToken,
                   @Param("expiresAt") OffsetDateTime expiresAt);

    @Delete("""
        DELETE FROM upload_dedup_claims
        WHERE app_id = #{appId}
          AND file_hash = #{fileHash}
          AND bucket_name = #{bucketName}
          AND owner_token = #{ownerToken}
        """)
    int releaseClaim(@Param("appId") String appId,
                     @Param("fileHash") String fileHash,
                     @Param("bucketName") String bucketName,
                     @Param("ownerToken") String ownerToken);
}
