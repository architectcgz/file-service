using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace FileService.Migrations
{
    /// <inheritdoc />
    public partial class InitialCreateWithNewSchema : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "services",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false, defaultValueSql: "gen_random_uuid()", comment: "主键ID"),
                    name = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false, comment: "服务名称"),
                    description = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true, comment: "服务描述"),
                    create_time = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", comment: "创建时间"),
                    update_time = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", comment: "更新时间"),
                    is_enabled = table.Column<bool>(type: "boolean", nullable: false, defaultValue: true, comment: "是否启用")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_services", x => x.id);
                });

            migrationBuilder.CreateTable(
                name: "buckets",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false, defaultValueSql: "gen_random_uuid()", comment: "主键ID"),
                    name = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false, comment: "存储桶名称"),
                    description = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true, comment: "存储桶描述"),
                    service_id = table.Column<Guid>(type: "uuid", nullable: false, comment: "所属服务ID"),
                    create_time = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", comment: "创建时间"),
                    update_time = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", comment: "更新时间"),
                    is_enabled = table.Column<bool>(type: "boolean", nullable: false, defaultValue: true, comment: "是否启用")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_buckets", x => x.id);
                    table.ForeignKey(
                        name: "fk_buckets_services_service_id",
                        column: x => x.service_id,
                        principalTable: "services",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateTable(
                name: "uploaded_files",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false, defaultValueSql: "gen_random_uuid()", comment: "主键ID"),
                    file_hash = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false, comment: "文件SHA256哈希值，用于去重"),
                    file_key = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: false, comment: "文件存储路径"),
                    file_url = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: false, comment: "文件访问URL"),
                    original_file_name = table.Column<string>(type: "character varying(255)", maxLength: 255, nullable: false, comment: "原始文件名称"),
                    file_size = table.Column<long>(type: "bigint", nullable: false, comment: "文件大小（字节）"),
                    content_type = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false, comment: "文件MIME类型"),
                    file_extension = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false, comment: "文件扩展名"),
                    service_id = table.Column<Guid>(type: "uuid", nullable: false, comment: "所属服务ID"),
                    bucket_id = table.Column<Guid>(type: "uuid", nullable: false, comment: "所属存储桶ID"),
                    reference_count = table.Column<int>(type: "integer", nullable: false, defaultValue: 1, comment: "文件引用计数"),
                    uploader_id = table.Column<string>(type: "character varying(450)", maxLength: 450, nullable: true, comment: "上传者用户ID"),
                    create_time = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", comment: "创建时间"),
                    last_access_time = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", comment: "最后访问时间"),
                    deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false, comment: "逻辑删除标志")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_uploaded_files", x => x.id);
                    table.ForeignKey(
                        name: "fk_uploaded_files_buckets_bucket_id",
                        column: x => x.bucket_id,
                        principalTable: "buckets",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                    table.ForeignKey(
                        name: "fk_uploaded_files_services_service_id",
                        column: x => x.service_id,
                        principalTable: "services",
                        principalColumn: "id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.CreateIndex(
                name: "ix_buckets_name",
                table: "buckets",
                column: "name",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_Buckets_Name_Unique",
                table: "buckets",
                column: "name",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_buckets_service_id",
                table: "buckets",
                column: "service_id");

            migrationBuilder.CreateIndex(
                name: "IX_Buckets_ServiceId",
                table: "buckets",
                column: "service_id");

            migrationBuilder.CreateIndex(
                name: "ix_services_name",
                table: "services",
                column: "name",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_Services_Name_Unique",
                table: "services",
                column: "name",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_bucket_id",
                table: "uploaded_files",
                column: "bucket_id");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_content_type",
                table: "uploaded_files",
                column: "content_type");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_content_type_create_time_id",
                table: "uploaded_files",
                columns: new[] { "content_type", "create_time", "id" });

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_create_time",
                table: "uploaded_files",
                column: "create_time");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_deleted",
                table: "uploaded_files",
                column: "deleted");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_deleted_uploader_id",
                table: "uploaded_files",
                columns: new[] { "deleted", "uploader_id" });

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_file_hash",
                table: "uploaded_files",
                column: "file_hash",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_reference_count",
                table: "uploaded_files",
                column: "reference_count");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_service_id_bucket_id",
                table: "uploaded_files",
                columns: new[] { "service_id", "bucket_id" });

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_uploader_id",
                table: "uploaded_files",
                column: "uploader_id");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_uploader_id_create_time_id",
                table: "uploaded_files",
                columns: new[] { "uploader_id", "create_time", "id" });

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_ContentType",
                table: "uploaded_files",
                column: "content_type");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_ContentType_CreateTime_Id_Desc",
                table: "uploaded_files",
                columns: new[] { "content_type", "create_time", "id" },
                descending: new[] { false, true, true });

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_CreateTime",
                table: "uploaded_files",
                column: "create_time");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_Deleted",
                table: "uploaded_files",
                column: "deleted");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_Deleted_UploaderId",
                table: "uploaded_files",
                columns: new[] { "deleted", "uploader_id" });

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_FileHash_Unique",
                table: "uploaded_files",
                column: "file_hash",
                unique: true,
                filter: "\"deleted\" = false");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_ReferenceCount",
                table: "uploaded_files",
                column: "reference_count");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_ServiceId_BucketId",
                table: "uploaded_files",
                columns: new[] { "service_id", "bucket_id" });

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_UploaderId",
                table: "uploaded_files",
                column: "uploader_id");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_UploaderId_CreateTime_Id_Desc",
                table: "uploaded_files",
                columns: new[] { "uploader_id", "create_time", "id" },
                descending: new[] { false, true, true });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "uploaded_files");

            migrationBuilder.DropTable(
                name: "buckets");

            migrationBuilder.DropTable(
                name: "services");
        }
    }
}
