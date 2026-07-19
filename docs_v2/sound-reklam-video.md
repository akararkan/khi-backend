# Sound Reklam Video API

Single global video (one record only, not per soundtrack), uploaded to S3.

## Base URL
`/api/v1/sound-tracks/sound-reklam-video`

## Data Model (`data` field)
```json
{
  "id": 1,
  "videoUrl": "https://s3-khiwebsite.s3.us-east-1.amazonaws.com/khi-web-folders/video/uuid-file.mp4",
  "sizeBytes": 12500432,
  "mimeType": "video/mp4",
  "createdAt": "2026-07-19T08:43:20",
  "updatedAt": "2026-07-19T08:43:20"
}
```

## API Response Wrapper
All successful non-delete endpoints return:
```json
{
  "success": true,
  "message": "Sound reklam video fetched successfully",
  "data": {}
}
```

## Endpoints

### 1) Create
- **POST** `/api/v1/sound-tracks/sound-reklam-video`
- **Content-Type:** `multipart/form-data`
- **Request part:** `videoFile` (required, must be `video/*`)
- **Request JSON body:** none

**Success (201):**
```json
{
  "success": true,
  "message": "Sound reklam video created successfully",
  "data": {
    "id": 1,
    "videoUrl": "https://.../video.mp4",
    "sizeBytes": 12500432,
    "mimeType": "video/mp4",
    "createdAt": "2026-07-19T08:43:20",
    "updatedAt": "2026-07-19T08:43:20"
  }
}
```

---

### 2) Get
- **GET** `/api/v1/sound-tracks/sound-reklam-video`
- **Request JSON body:** none

**Success (200):**
```json
{
  "success": true,
  "message": "Sound reklam video fetched successfully",
  "data": {
    "id": 1,
    "videoUrl": "https://.../video.mp4",
    "sizeBytes": 12500432,
    "mimeType": "video/mp4",
    "createdAt": "2026-07-19T08:43:20",
    "updatedAt": "2026-07-19T08:43:20"
  }
}
```

---

### 3) Update
- **PATCH** `/api/v1/sound-tracks/sound-reklam-video`
- **Content-Type:** `multipart/form-data`
- **Request part:** `videoFile` (required, must be `video/*`)
- **Request JSON body:** none

**Success (200):**
```json
{
  "success": true,
  "message": "Sound reklam video updated successfully",
  "data": {
    "id": 1,
    "videoUrl": "https://.../new-video.mp4",
    "sizeBytes": 9800432,
    "mimeType": "video/mp4",
    "createdAt": "2026-07-19T08:43:20",
    "updatedAt": "2026-07-19T08:50:10"
  }
}
```

---

### 4) Delete
- **DELETE** `/api/v1/sound-tracks/sound-reklam-video`
- **Request JSON body:** none

**Success:** `204 No Content` (empty body)

## Common Error Response Shape
```json
{
  "timestamp": "2026-07-19T08:50:12.115Z",
  "status": 404,
  "path": "/api/v1/sound-tracks/sound-reklam-video",
  "method": "GET",
  "traceId": "abc123",
  "code": "NOT_FOUND",
  "message": "Sound reklam video was not found.",
  "messageEn": "Sound reklam video was not found.",
  "messageKu": "ڤیدیۆی رێکلامی ساوند نەدۆزرایەوە.",
  "fieldErrors": [],
  "details": {}
}
```

## Notes
- Only one reklam video is allowed in the system.
- `POST` returns validation error if one already exists.
- `PATCH` replaces the video file and removes old S3 file.
