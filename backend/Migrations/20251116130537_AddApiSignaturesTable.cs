using System;
using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace FileService.Migrations
{
    /// <inheritdoc />
    public partial class AddApiSignaturesTable : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "api_signatures",
                columns: table => new
                {
                    id = table.Column<long>(type: "bigint", nullable: false)
                        .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn),
                    signature_token = table.Column<string>(type: "character varying(128)", maxLength: 128, nullable: false),
                    caller_service = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    caller_service_id = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: true),
                    allowed_operation = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    allowed_file_types = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: true),
                    max_file_size = table.Column<long>(type: "bigint", nullable: true),
                    status = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false, defaultValue: "active"),
                    created_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false, defaultValueSql: "CURRENT_TIMESTAMP AT TIME ZONE 'UTC'"),
                    expires_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: false),
                    last_used_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    usage_count = table.Column<int>(type: "integer", nullable: false, defaultValue: 0),
                    max_usage_count = table.Column<int>(type: "integer", nullable: false, defaultValue: 0),
                    notes = table.Column<string>(type: "character varying(500)", maxLength: 500, nullable: true),
                    revoked_at = table.Column<DateTime>(type: "timestamp with time zone", nullable: true),
                    revoke_reason = table.Column<string>(type: "character varying(200)", maxLength: 200, nullable: true),
                    creator_ip = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("pk_api_signatures", x => x.id);
                });

            migrationBuilder.CreateIndex(
                name: "IX_ApiSignatures_CallerService",
                table: "api_signatures",
                column: "caller_service");

            migrationBuilder.CreateIndex(
                name: "IX_ApiSignatures_CreatedAt",
                table: "api_signatures",
                column: "created_at",
                descending: new bool[0]);

            migrationBuilder.CreateIndex(
                name: "IX_ApiSignatures_SignatureToken_Unique",
                table: "api_signatures",
                column: "signature_token",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_ApiSignatures_Status_ExpiresAt",
                table: "api_signatures",
                columns: new[] { "status", "expires_at" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "api_signatures");
        }
    }
}
