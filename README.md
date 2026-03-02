# PDF Layer Merge Service

A Java REST web service that merges two geospatial PDF files (from ArcGIS/QGIS) into one, preserving all layers (OCGs) and geospatial metadata (coordinate systems, viewports).

## What It Does

- Accepts two PDF files via HTTP POST
- Merges pages from both into a single PDF
- Preserves all layers from both sources, grouped under the source filename in the layer panel
- Preserves geospatial metadata (coordinate systems, viewports, projections) on every page
- Returns the merged PDF as a download

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 |
| Web framework | Spring Boot 3.4 |
| PDF library | iText 8 (AGPL) |
| Build tool | Maven |
| Container | Docker |

## Project Structure

```
pdf_layer_merge/
  data/                          # Sample PDFs for testing
    Layout_merge_1.pdf
    Layout_merge_2.pdf
  src/main/java/com/geomerge/
    PdfLayerMergeApplication.java       # Spring Boot entry point
    controller/
      MergeController.java              # REST endpoints
    service/
      PdfMergeService.java              # Merge orchestration
      OcgMergeService.java              # Layer/OCG hierarchy merging
      GeoMetadataService.java           # Geospatial metadata verification
    exception/
      PdfMergeException.java            # Custom exception
      GlobalExceptionHandler.java       # Error response formatting
  src/main/resources/
    application.properties             # Server config
  Dockerfile                           # Two-stage Docker build
  docker-compose.yml                   # Docker Compose config
  pom.xml                              # Maven dependencies
```

## Running the Service

### Option 1: Docker (recommended)

Requires [Docker Desktop](https://www.docker.com/products/docker-desktop/).

```cmd
docker compose up --build
```

### Option 2: Maven (for development)

Requires Java 21 and Maven installed.

```cmd
mvn spring-boot:run
```

The service starts on **http://localhost:8080**.

---

## API Endpoints

### POST /api/merge

Merge two PDF files uploaded directly.

**Request:** `multipart/form-data`

| Field | Type | Description |
|-------|------|-------------|
| `file1` | File | First PDF (max 100MB) |
| `file2` | File | Second PDF (max 100MB) |

**Response:** `application/pdf` — the merged PDF as a download.

**Sample call using the test files in `/data`:**

```cmd
curl -X POST http://localhost:8080/api/merge -F "file1=@C:\side_projects\pdf_layer_merge\data\Layout_merge_1.pdf" -F "file2=@C:\side_projects\pdf_layer_merge\data\Layout_merge_2.pdf" -o "C:\side_projects\pdf_layer_merge\data\merged.pdf"
```

---

### POST /api/merge-urls

Merge two PDFs by URL (e.g. from an ArcGIS Server export endpoint).

**Request:** `application/x-www-form-urlencoded`

| Field | Type | Description |
|-------|------|-------------|
| `url1` | String | HTTPS URL to first PDF |
| `url2` | String | HTTPS URL to second PDF |

**Response:** `application/pdf` — the merged PDF as a download.

**Sample call:**

```cmd
curl -X POST http://localhost:8080/api/merge-urls -d "url1=https://example.com/map1.pdf" -d "url2=https://example.com/map2.pdf" -o "data\merged.pdf"
```

---

## Error Responses

All errors return JSON:

```json
{ "error": "Description of what went wrong" }
```

| HTTP Status | Cause |
|-------------|-------|
| 400 | Invalid or non-PDF file, missing parameters |
| 413 | File exceeds 100MB limit |
| 500 | Unexpected server error |

---

## Layer Hierarchy in Merged PDF

Layers from each source PDF are grouped under the source filename in the PDF viewer's layer panel:

```
Layout_merge_1
  ├── Roads
  ├── Parcels
  └── ...
Layout_merge_2
  ├── Roads
  ├── Parcels
  └── ...
```

Duplicate layer names across files are kept as-is since the parent group distinguishes them.

## License

iText 8 is used under the [AGPL license](https://www.gnu.org/licenses/agpl-3.0.html). This project is for personal/open-source use.
