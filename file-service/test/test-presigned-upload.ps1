# Presigned Upload API Test Script
# Tests the presigned single upload-session flow

param(
    [string]$ConfigPath = "../config/test-env.json",
    [string]$UploadServiceUrl = "http://localhost:8089"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Presigned Upload API Tests" -ForegroundColor Cyan
Write-Host "Service URL: $UploadServiceUrl" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test Results
$TestResults = @()

function Add-TestResult {
    param([string]$TestId, [string]$TestName, [string]$Status, [string]$ResponseTime, [string]$Note)
    $script:TestResults += [PSCustomObject]@{
        TestId = $TestId; TestName = $TestName; Status = $Status
        ResponseTime = $ResponseTime; Note = $Note
    }
}

function Invoke-ApiRequest {
    param([string]$Method, [string]$Url, [object]$Body = $null, [hashtable]$Headers = @{})
    $StartTime = Get-Date
    $Result = @{ Success = $false; StatusCode = 0; Body = $null; ResponseTime = 0; Error = "" }
    try {
        $RequestParams = @{ Method = $Method; Uri = $Url; ContentType = "application/json"; Headers = $Headers; ErrorAction = "Stop" }
        if ($Body) { $RequestParams.Body = ($Body | ConvertTo-Json -Depth 10) }
        $Response = Invoke-WebRequest @RequestParams
        $Result.Success = $true
        $Result.StatusCode = $Response.StatusCode
        $Result.Body = $Response.Content | ConvertFrom-Json
    }
    catch {
        if ($_.Exception.Response) {
            $Result.StatusCode = [int]$_.Exception.Response.StatusCode
            try {
                $StreamReader = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
                $Result.Body = $StreamReader.ReadToEnd() | ConvertFrom-Json
                $StreamReader.Close()
            } catch { $Result.Error = $_.Exception.Message }
        } else { $Result.Error = $_.Exception.Message }
    }
    $Result.ResponseTime = [math]::Round(((Get-Date) - $StartTime).TotalMilliseconds)
    return $Result
}

# Global variables
$Global:AppId = "blog"
$Global:UserId = 1

# === TEST 1: Create Upload Session ===
Write-Host "[TEST-1] Creating PRESIGNED_SINGLE upload session..." -ForegroundColor Yellow
$PresignBody = @{
    uploadMode = "PRESIGNED_SINGLE"
    accessLevel = "PUBLIC"
    originalFilename = "test-presigned-$(Get-Date -Format 'yyyyMMddHHmmss').mp4"
    contentType = "video/mp4"
    expectedSize = 1048576
    fileHash = "$(New-Guid)".Replace("-", "")
}
$Headers = @{ "X-App-Id" = $Global:AppId; "X-User-Id" = $Global:UserId }
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload-sessions" -Body $PresignBody -Headers $Headers

if ($Result.Success -and $Result.Body.uploadSession.uploadSessionId) {
    $UploadSessionId = $Result.Body.uploadSession.uploadSessionId
    $PresignedUrl = $Result.Body.singleUploadUrl
    $ExpiresIn = $Result.Body.singleUploadExpiresInSeconds
    
    Add-TestResult -TestId "TEST-1" -TestName "Create Upload Session" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Upload session created successfully"
    Write-Host "  PASS - Upload session created ($($Result.ResponseTime)ms)" -ForegroundColor Green
    Write-Host "    Upload Session ID: $UploadSessionId" -ForegroundColor Gray
    Write-Host "    Upload URL Present: $([string]::IsNullOrEmpty($PresignedUrl) -eq $false)" -ForegroundColor Gray
    Write-Host "    Expires In: $ExpiresIn" -ForegroundColor Gray
} else {
    $ErrorMsg = if ($Result.Body.message) { $Result.Body.message } else { $Result.Error }
    Add-TestResult -TestId "TEST-1" -TestName "Create Upload Session" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === TEST 2: Create Session Again With Same Hash ===
Write-Host "[TEST-2] Creating session again with the same hash..." -ForegroundColor Yellow
$ExistingFileHash = "d41d8cd98f00b204e9800998ecf8427e"
$PresignBody2 = @{
    uploadMode = "PRESIGNED_SINGLE"
    accessLevel = "PUBLIC"
    originalFilename = "existing-file.mp4"
    contentType = "video/mp4"
    expectedSize = 1048576
    fileHash = $ExistingFileHash
}

# First request - should succeed
$Result1 = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload-sessions" -Body $PresignBody2 -Headers $Headers

$Result2 = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload-sessions" -Body $PresignBody2 -Headers $Headers

if ($Result1.Success -and $Result2.Success -and $Result2.Body.uploadSession.uploadSessionId) {
    $IsResumed = $Result2.Body.resumed
    Add-TestResult -TestId "TEST-2" -TestName "Reuse Upload Session" -Status "PASS" -ResponseTime "$($Result2.ResponseTime)ms" -Note "Second request succeeded; resumed=$IsResumed"
    Write-Host "  PASS - Second request succeeded, resumed=$IsResumed ($($Result2.ResponseTime)ms)" -ForegroundColor Green
} else {
    $ErrorMsg = if ($Result2.Body.message) { $Result2.Body.message } else { $Result2.Error }
    Add-TestResult -TestId "TEST-2" -TestName "Reuse Upload Session" -Status "FAIL" -ResponseTime "$($Result2.ResponseTime)ms" -Note $ErrorMsg
    Write-Host "  FAIL - $ErrorMsg ($($Result2.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === TEST 3: Complete Upload Without S3 Object ===
Write-Host "[TEST-3] Completing upload without S3 object (expected to fail)..." -ForegroundColor Yellow
$ConfirmBody = @{
    contentType = "video/mp4"
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload-sessions/$UploadSessionId/complete" -Body $ConfirmBody -Headers $Headers

if (-not $Result.Success) {
    Add-TestResult -TestId "TEST-3" -TestName "Complete Upload Without S3 File" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected - file not in S3"
    Write-Host "  PASS - Correctly rejected upload completion (file not in S3) ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TEST-3" -TestName "Complete Upload Without S3 File" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should have rejected"
    Write-Host "  FAIL - Should have rejected completion ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === TEST 4: Invalid Request - Missing Fields ===
Write-Host "[TEST-4] Testing invalid upload-session request (missing fields)..." -ForegroundColor Yellow
$InvalidBody = @{
    uploadMode = "PRESIGNED_SINGLE"
    originalFilename = "test.mp4"
    # Missing accessLevel, contentType, expectedSize, fileHash
}
$Result = Invoke-ApiRequest -Method "POST" -Url "$UploadServiceUrl/api/v1/upload-sessions" -Body $InvalidBody -Headers $Headers

if ($Result.StatusCode -eq 400 -or ($Result.Body -and $Result.Body.code -ne 200)) {
    Add-TestResult -TestId "TEST-4" -TestName "Invalid Request Validation" -Status "PASS" -ResponseTime "$($Result.ResponseTime)ms" -Note "Correctly rejected invalid request"
    Write-Host "  PASS - Correctly rejected invalid request ($($Result.ResponseTime)ms)" -ForegroundColor Green
} else {
    Add-TestResult -TestId "TEST-4" -TestName "Invalid Request Validation" -Status "FAIL" -ResponseTime "$($Result.ResponseTime)ms" -Note "Should have rejected"
    Write-Host "  FAIL - Should have rejected invalid request ($($Result.ResponseTime)ms)" -ForegroundColor Red
}

Write-Host ""

# === Test Results Summary ===
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test Results Summary" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$PassCount = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$FailCount = ($TestResults | Where-Object { $_.Status -eq "FAIL" }).Count
$SkipCount = ($TestResults | Where-Object { $_.Status -eq "SKIP" }).Count

Write-Host "Total Tests: $($TestResults.Count)" -ForegroundColor White
Write-Host "Passed: $PassCount" -ForegroundColor Green
Write-Host "Failed: $FailCount" -ForegroundColor Red
Write-Host "Skipped: $SkipCount" -ForegroundColor Gray
Write-Host ""

# Display detailed results
$TestResults | Format-Table -AutoSize

if ($FailCount -eq 0) {
    Write-Host "All tests passed!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "Some tests failed!" -ForegroundColor Red
    exit 1
}
