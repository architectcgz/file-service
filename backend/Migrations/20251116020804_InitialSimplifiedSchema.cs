using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace FileService.Migrations
{
    /// <inheritdoc />
    public partial class InitialSimplifiedSchema : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "uploaded_files",
                columns: table => new
                {
                    id = table.Column<Guid>(type: "uuid", nullable: false, defaultValueSql: "gen_random_uuid()", comment: "主键ID"),
                    file_hash = table.Column<string>(type: "character varying(64)", maxLength: 64, nullable: false, comment: "文件SHA256哈希值，用于去重"),
                    file_key = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: false, comment: "文件存储路径"),
                    file_url = table.Column<string>(type: "character varying(1000)", maxLength: 1000, nullable: false, comment: "文件访问URL"),
                    reference_count = table.Column<int>(type: "integer", nullable: false, defaultValue: 1, comment: "文件引用计数"),
                    uploader_id = table.Column<string>(type: "character varying(450)", maxLength: 450, nullable: true, comment: "上传者用户ID"),
                    create_time = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", comment: "创建时间"),
                    last_access_time = table.Column<DateTimeOffset>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'", comment: "最后访问时间"),
                    upload_status = table.Column<int>(type: "integer", nullable: false, comment: "上传状态"),
                    deleted = table.Column<bool>(type: "boolean", nullable: false, defaultValue: false, comment: "逻辑删除标志")
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_uploaded_files", x => x.id);
                });

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_create_time",
                table: "uploaded_files",
                column: "create_time");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_deleted",
                table: "uploaded_files",
                column: "deleted");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_file_hash",
                table: "uploaded_files",
                column: "file_hash",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_upload_status",
                table: "uploaded_files",
                column: "upload_status");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_uploader_id",
                table: "uploaded_files",
                column: "uploader_id");

            migrationBuilder.CreateIndex(
                name: "ix_uploaded_files_uploader_id_deleted_create_time",
                table: "uploaded_files",
                columns: new[] { "uploader_id", "deleted", "create_time" });

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_CreateTime",
                table: "uploaded_files",
                column: "create_time",
                descending: new bool[0]);

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_Deleted",
                table: "uploaded_files",
                column: "deleted");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_FileHash_Unique",
                table: "uploaded_files",
                column: "file_hash",
                unique: true,
                filter: "\"deleted\" = false");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_UploaderId",
                table: "uploaded_files",
                column: "uploader_id");

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_UploaderId_Deleted_CreateTime",
                table: "uploaded_files",
                columns: new[] { "uploader_id", "deleted", "create_time" },
                descending: new[] { false, false, true });

            migrationBuilder.CreateIndex(
                name: "IX_UploadedFiles_UploadStatus",
                table: "uploaded_files",
                column: "upload_status");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "uploaded_files");
        }
    }
}
