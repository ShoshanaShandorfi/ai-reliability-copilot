$files = @(
 "big-log.txt",
 "big-log-2.txt",
 "big-log-3.txt",
 "log1.txt"
)

$basePath = "C:\git\AI Reliability Copilot\src\main\resources\demo-logs"

$ids = @()

Write-Output "=== STEP 1: Sending POST requests ==="

# 🔥 POST בלי sleep
foreach ($f in $files) {
    for ($i = 0; $i -lt 5; $i++) {

        $full = Join-Path $basePath $f

        $response = curl -s -X POST http://localhost:8080/logs/file -F "file=@$full"

        try {
            $parsed = $response | ConvertFrom-Json
            if ($parsed.id) {
                $ids += $parsed.id
                Write-Output "Added ID: $($parsed.id)"
            } else {
                Write-Output "Invalid response: $response"
            }
        } catch {
            Write-Output "Failed parsing response: $response"
        }
    }
}

Write-Output "=== POST DONE ==="
Write-Output "Total IDs: $($ids.Count)"

Write-Output "=== STEP 2: Running GET in parallel ==="

# 🔥 GET במקביל
foreach ($id in $ids) {

    Start-Job -ScriptBlock {
        param($id)

        do {
            $result = curl -s http://localhost:8080/logs/analysis/$id
            try {
                $parsed = $result | ConvertFrom-Json
            } catch {
                break
            }

            if ($parsed.status -eq "PROCESSING") {
                Start-Sleep -Milliseconds 500
            }

        } while ($parsed.status -eq "PROCESSING")

        Write-Output "===== RESULT $id ====="
        Write-Output $result

    } -ArgumentList $id
}

Write-Output "=== ALL GET JOBS SUBMITTED ==="