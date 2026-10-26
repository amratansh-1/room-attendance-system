# End-to-end demo of the attendance API for Windows PowerShell.
# Needs: the app running on localhost:8080 and a FRESH database (only the admin exists).
# Run:  powershell -ExecutionPolicy Bypass -File scripts\demo.ps1

function Api($user, $pass, $method, $path, $body = $null) {
  $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${user}:${pass}"))
  $h = @{ Authorization = "Basic $b64" }
  $uri = "http://localhost:8080$path"
  Write-Host "`n>>> $method $path   (as $user)" -ForegroundColor Cyan
  try {
    if ($body) { $r = Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType "application/json" -Body $body }
    else       { $r = Invoke-RestMethod -Uri $uri -Method $method -Headers $h }
    if ($null -eq $r -or "$r" -eq "") { Write-Host "OK (no content)" -ForegroundColor Green }
    elseif ($r -is [string]) { Write-Host $r }
    else { $r | ConvertTo-Json -Depth 6 }
  } catch {
    Write-Host "FAILED  HTTP $([int]$_.Exception.Response.StatusCode)" -ForegroundColor Yellow
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message -ForegroundColor Yellow }
  }
}

Write-Host "`n##### 1. Admin creates two observers" -ForegroundColor Magenta
Api admin admin123 POST /api/admin/users '{"username":"obs1","password":"observer123","fullName":"Observer One","role":"OBSERVER"}'
Api admin admin123 POST /api/admin/users '{"username":"obs2","password":"observer123","fullName":"Observer Two","role":"OBSERVER"}'

Write-Host "`n##### 2. Admin creates rooms" -ForegroundColor Magenta
Api admin admin123 POST /api/admin/rooms '{"roomNo":"R101","capacity":3}'
Api admin admin123 POST /api/admin/rooms '{"roomNo":"R102","capacity":2}'

Write-Host "`n##### 3. Admin adds persons to rooms" -ForegroundColor Magenta
Api admin admin123 POST /api/admin/rooms/1/persons/bulk '{"persons":[{"personCode":"S101","fullName":"Rahul Sharma"},{"personCode":"S102","fullName":"Neha Verma"},{"personCode":"S103","fullName":"Amit Singh"}]}'
Api admin admin123 POST /api/admin/rooms/2/persons/bulk '{"persons":[{"personCode":"S201","fullName":"Priya Gupta"},{"personCode":"S202","fullName":"Karan Mehta"}]}'

Write-Host "`n##### 4. Admin assigns observers to rooms" -ForegroundColor Magenta
Api admin admin123 POST /api/admin/rooms/1/observers/2
Api admin admin123 POST /api/admin/rooms/2/observers/3

Write-Host "`n##### 5. TRIGGER TEST: 4th person in a full room (expect FAILED 409)" -ForegroundColor Magenta
Api admin admin123 POST /api/admin/rooms/1/persons '{"personCode":"S104","fullName":"Too Many"}'

Write-Host "`n##### 6. Observer 1 marks attendance" -ForegroundColor Magenta
Api obs1 observer123 GET /api/observer/rooms
Api obs1 observer123 GET /api/observer/rooms/1/persons
Api obs1 observer123 POST /api/observer/attendance/bulk '{"entries":[{"personId":3,"status":"LATE"},{"personId":1,"status":"PRESENT"},{"personId":2,"status":"ABSENT"}]}'

Write-Host "`n##### 7. Observer 1 corrects S102 (audit table records it)" -ForegroundColor Magenta
Api obs1 observer123 POST /api/observer/attendance '{"personId":2,"status":"LATE"}'
Api obs1 observer123 POST /api/observer/attendance '{"personId":2,"status":"ABSENT"}'

Write-Host "`n##### 8. SECURITY TEST: obs1 marks a person of another room (expect FAILED 403)" -ForegroundColor Magenta
Api obs1 observer123 POST /api/observer/attendance '{"personId":4,"status":"PRESENT"}'

Write-Host "`n##### 9. Observer 2 marks room R102" -ForegroundColor Magenta
Api obs2 observer123 POST /api/observer/attendance '{"personId":4,"status":"PRESENT"}'
Api obs2 observer123 POST /api/observer/attendance '{"personId":5,"status":"ABSENT"}'

Write-Host "`n##### 10. ADMIN REPORT" -ForegroundColor Magenta
Api admin admin123 GET /api/admin/reports/attendance
Api admin admin123 GET /api/admin/reports/attendance/csv

Write-Host "`n##### 11. Detailed rows (person, room, date, observer)" -ForegroundColor Magenta
Api admin admin123 GET /api/admin/attendance
