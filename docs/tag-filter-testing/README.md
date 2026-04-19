# Tag Filter Testing

This guide walks through manually testing scene tag filtering in the backend.

These steps assume Windows PowerShell and start from cloning the repository.

If Docker Compose is not installed yet, first follow [Install Docker Compose](../docker-compose-install/README.md).

## Goal

Verify that:

- `GET /api/scenes?tag=ambient` returns only scenes linked to the `ambient` tag
- tag filtering is case-insensitive and whitespace-tolerant
- the endpoint returns `[]` when no scenes match

## 1. Clone the Repository

```powershell
git clone https://github.com/B2MAGE/mage-backend.git
cd mage-backend
```

## 2. Check Out the Filtering Branch

If the filtering work has already been merged into the branch you want to test, you can skip this step.

```powershell
git checkout 24-filter-scenes-by-tag
```

## 3. Create the Local Environment File

```powershell
Copy-Item .env.example .env
```

## 4. Start the Backend and Database

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
```

Wait until the backend is healthy and listening on `http://localhost:8080`.

## 5. Open a Second PowerShell Window

Use the second terminal for the API requests below.

## 6. Register a User

```powershell
$register = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/auth/register" `
  -ContentType "application/json" `
  -Body '{"email":"filter-test@example.com","password":"test-password","firstName":"Filter","lastName":"Tester","displayName":"Filter Test User"}'
```

## 7. Log In and Save the Access Token

```powershell
$login = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"email":"filter-test@example.com","password":"test-password"}'

$token = $login.accessToken
```

## 8. Create Two Tags

```powershell
$ambientTag = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/tags" `
  -ContentType "application/json" `
  -Body '{"name":"ambient"}'

$showcaseTag = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/tags" `
  -ContentType "application/json" `
  -Body '{"name":"showcase"}'
```

## 9. Create Two Scenes

```powershell
$scene1 = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/scenes" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body '{"name":"Aurora Drift","sceneData":{"visualizer":{"shader":"nebula"}}}'

$scene2 = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/scenes" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body '{"name":"Signal Bloom","sceneData":{"visualizer":{"shader":"pulse"}}}'
```

## 10. Attach Different Tags to the Two Scenes

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/scenes/$($scene1.sceneId)/tags" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body "{""tagId"":$($ambientTag.tagId)}"

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/scenes/$($scene2.sceneId)/tags" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body "{""tagId"":$($showcaseTag.tagId)}"
```

## 11. Test the Filter

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/scenes?tag=ambient" `
  -Headers @{ Authorization = "Bearer $token" }
```

Expected result:

- only `Aurora Drift` is returned

## 12. Test Tag Normalization

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/scenes?tag=%20AMBIENT%20" `
  -Headers @{ Authorization = "Bearer $token" }
```

Expected result:

- only `Aurora Drift` is returned again

## 13. Test the Empty-List Case

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/scenes?tag=does-not-exist" `
  -Headers @{ Authorization = "Bearer $token" }
```

Expected result:

- the response body is `[]`

## 14. Optional: Compare Against the Unfiltered List

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/scenes" `
  -Headers @{ Authorization = "Bearer $token" }
```

Expected result:

- both scenes are returned

## 15. Stop the Stack When Finished

In the terminal running Docker Compose:

```powershell
Ctrl+C
```

To remove containers afterward:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml down
```
