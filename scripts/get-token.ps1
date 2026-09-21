# Fetches an access token for a demo user so you can call the API from curl or Swagger UI.
#   .\scripts\get-token.ps1 analyst analyst123
param(
    [Parameter(Mandatory = $true)][string]$Username,
    [Parameter(Mandatory = $true)][string]$Password,
    [string]$BaseUrl = "http://localhost:8081"
)
$body = @{
    client_id  = "credisynch-web"
    grant_type = "password"
    username   = $Username
    password   = $Password
}
$response = Invoke-RestMethod -Method Post `
    -Uri "$($BaseUrl.TrimEnd('/'))/realms/credisynch/protocol/openid-connect/token" `
    -ContentType "application/x-www-form-urlencoded" -Body $body
$response.access_token
