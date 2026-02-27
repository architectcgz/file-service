package com.architectcgz.file.infrastructure.cache;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * UploadRedisKeys 单元测试
 */
class UploadRedisKeysTest {

    // ==================== partsBitmap() 测试 ====================

    @Test
    void partsBitmap_shouldReturnCorrectKey_whenValidTaskId() {
        // Given
        String taskId = "task-123-abc";

        // When
        String key = UploadRedisKeys.partsBitmap(taskId);

        // Then
        assertThat(key).isEqualTo("upload:task:task-123-abc:parts");
    }

    @Test
    void partsBitmap_shouldReturnCorrectKey_whenTaskIdWithSpecialCharacters() {
        // Given
        String taskId = "01JGXXX-YYY-ZZZ";

        // When
        String key = UploadRedisKeys.partsBitmap(taskId);

        // Then
        assertThat(key).isEqualTo("upload:task:01JGXXX-YYY-ZZZ:parts");
    }

    @Test
    void partsBitmap_shouldThrowException_whenTaskIdIsNull() {
        // When & Then
        assertThatThrownBy(() -> UploadRedisKeys.partsBitmap(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId 不能为 null 或空字符串");
    }

    @Test
    void partsBitmap_shouldThrowException_whenTaskIdIsEmpty() {
        // When & Then
        assertThatThrownBy(() -> UploadRedisKeys.partsBitmap(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId 不能为 null 或空字符串");
    }

    @Test
    void partsBitmap_shouldThrowException_whenTaskIdIsBlank() {
        // When & Then
        assertThatThrownBy(() -> UploadRedisKeys.partsBitmap("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskId 不能为 null 或空字符串");
    }

    // ==================== getBitOffset() 测试 ====================

    @Test
    void getBitOffset_shouldReturnZero_whenPartNumberIsOne() {
        // When
        long offset = UploadRedisKeys.getBitOffset(1);

        // Then
        assertThat(offset).isEqualTo(0L);
    }

    @Test
    void getBitOffset_shouldReturnCorrectOffset_whenPartNumberIsTwo() {
        // When
        long offset = UploadRedisKeys.getBitOffset(2);

        // Then
        assertThat(offset).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000, 10000})
    void getBitOffset_shouldReturnCorrectOffset_forVariousPartNumbers(int partNumber) {
        // When
        long offset = UploadRedisKeys.getBitOffset(partNumber);

        // Then
        assertThat(offset).isEqualTo(partNumber - 1L);
    }

    @Test
    void getBitOffset_shouldThrowException_whenPartNumberIsZero() {
        // When & Then
        assertThatThrownBy(() -> UploadRedisKeys.getBitOffset(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片编号必须 >= 1");
    }

    @Test
    void getBitOffset_shouldThrowException_whenPartNumberIsNegative() {
        // When & Then
        assertThatThrownBy(() -> UploadRedisKeys.getBitOffset(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分片编号必须 >= 1");
    }

    // ==================== getPartNumber() 测试 ====================

    @Test
    void getPartNumber_shouldReturnOne_whenBitOffsetIsZero() {
        // When
        int partNumber = UploadRedisKeys.getPartNumber(0L);

        // Then
        assertThat(partNumber).isEqualTo(1);
    }

    @Test
    void getPartNumber_shouldReturnTwo_whenBitOffsetIsOne() {
        // When
        int partNumber = UploadRedisKeys.getPartNumber(1L);

        // Then
        assertThat(partNumber).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 9, 99, 999, 9999})
    void getPartNumber_shouldReturnCorrectPartNumber_forVariousBitOffsets(long bitOffset) {
        // When
        int partNumber = UploadRedisKeys.getPartNumber(bitOffset);

        // Then
        assertThat(partNumber).isEqualTo((int) (bitOffset + 1));
    }

    @Test
    void getPartNumber_shouldThrowException_whenBitOffsetIsNegative() {
        // When & Then
        assertThatThrownBy(() -> UploadRedisKeys.getPartNumber(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bitmap 位偏移量必须 >= 0");
    }

    // ==================== 往返转换测试 ====================

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 100, 1000, 10000})
    void roundTrip_partNumberToBitOffsetAndBack_shouldReturnOriginalValue(int originalPartNumber) {
        // When
        long bitOffset = UploadRedisKeys.getBitOffset(originalPartNumber);
        int resultPartNumber = UploadRedisKeys.getPartNumber(bitOffset);

        // Then
        assertThat(resultPartNumber).isEqualTo(originalPartNumber);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 4, 9, 99, 999, 9999})
    void roundTrip_bitOffsetToPartNumberAndBack_shouldReturnOriginalValue(long originalBitOffset) {
        // When
        int partNumber = UploadRedisKeys.getPartNumber(originalBitOffset);
        long resultBitOffset = UploadRedisKeys.getBitOffset(partNumber);

        // Then
        assertThat(resultBitOffset).isEqualTo(originalBitOffset);
    }

    // ==================== Key 格式一致性测试 ====================

    @Test
    void partsBitmap_shouldFollowNamingConvention() {
        // Given
        String taskId = "test-task-id";

        // When
        String key = UploadRedisKeys.partsBitmap(taskId);

        // Then
        assertThat(key)
                .startsWith("upload:task:")
                .endsWith(":parts")
                .contains(taskId);
    }

    @Test
    void partsBitmap_shouldProduceDifferentKeys_forDifferentTaskIds() {
        // Given
        String taskId1 = "task-1";
        String taskId2 = "task-2";

        // When
        String key1 = UploadRedisKeys.partsBitmap(taskId1);
        String key2 = UploadRedisKeys.partsBitmap(taskId2);

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }
}
