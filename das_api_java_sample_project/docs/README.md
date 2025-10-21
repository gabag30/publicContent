# WIPO DAS Java Sample Project

This sample demonstrates how to interact with the WIPO Digital Access Service (DAS) API to:

- Upload and register priority documents (Registration flow)
- Retrieve priority documents from DAS (Retrieval flow)
- Download registration certificates

It includes working examples for OAuth2 client-assertion authentication, file upload/download via pre-signed URLs, and polling for processing status.

---

## Quick Start

- Prerequisites:
  - Java 11+
  - Maven 3.6+
  - A valid EC private key (ES256) and client credentials for WIPO OAuth2
  - Network access to the configured OAuth and DAS endpoints

- Build:
  - `./build_project.sh`

- Run one of the sample flows:
  - `./run_test.sh`
    - 1: Registration test (upload + register)
    - 2: Retrieval test (request + download)
    - 3: Certificate download (for previously registered entries)

---

## Project Structure

- `src/main/java/org/wipo/das/assertion/JwtAssertionGenerator.java`
  - Builds a signed JWT (ES256) client assertion from an EC private key.

- `src/main/java/org/wipo/das/requests/`
  - `GetToken`: Exchanges a client-assertion JWT for an OAuth2 access token.
  - `ObtainFileIdAndUploadUrl`: Requests a `fileId` and pre-signed `fileUploadUrl`.
  - `UploadFileToDas`: Performs the file upload to the pre-signed URL (HTTP PUT).
  - `CheckFileStatus`: Polls DAS for file processing status by `fileId`.
  - `RegisterFile`: Submits a registration request for an uploaded file.
  - `GetUpdatedUploadUrl`: Refreshes the `fileUploadUrl` when expired.
  - `GetOsfAckId`: Registers a retrieval request and returns the acknowledgment id.
  - `GetFileFromDas`: Gets a pre-signed `fileDownloadUrl` for a retrieval.
  - `GetCertificateFromDas`: Downloads a registration certificate PDF.

- `src/main/java/org/wipo/das/restapitest/`
  - `ConfigManager`: Loads `config/config.properties` and CSV files, updates CSV state, and configures Log4j.
  - `RegistrationTest`: End-to-end upload + register using `registration_test.csv`.
  - `RetrievalTest`: End-to-end retrieval + download using `retrieval_test.csv`.
  - `CertificateDownloadTest`: Downloads registration certificates for registered rows.

- `config/`
  - `config.properties`: Endpoints, OAuth, columns mapping, local download folder.
  - `registration_test.csv`: Sample inputs for upload + register.
  - `retrieval_test.csv`: Sample inputs for retrieval + download.

---

## Configuration

Edit `config/config.properties`:

- `pemFile`: Path to your EC private key in PEM (PKCS#8 or keypair) format. Example: `../das_test_keys/acc_keys/ibpct1_acc_es256_private.pem`.
- `log4jConfigPath`: Path to Log4j 2 XML config (e.g., `./src/main/resources/log4j.xml`).
- `issuer`: OAuth authorization server base (e.g., `https://www5.wipo.int/am/oauth2`).
- `audience`: Token endpoint audience (e.g., `https://www5.wipo.int/am/oauth2/access_token`).
- `clientId`: OAuth client id registered with WIPO.
- `scope`: Required scope (e.g., `das-api/office-exchange`).
- `url`: DAS API base URL (e.g., `https://das-api.das.ipobs.acc.web1.wipo.int/das-api/v1/requests`).
- CSV Columns mapping (0-based index in code as strings):
  - `columnFileId`, `columnRegistered`, `columnAckId`
  - `columnOsfAckId`, `columnDownloaded`
- `localFolder`: Destination for downloaded PDFs (created if absent), e.g., `downloads`.

CSV files:

- `config/registration_test.csv` header:
  - `file_reference,file_location,application_number,application_date,priority_number,priority_date,document_category,application_category,das_code,file_id,registered,ack_id`

- `config/retrieval_test.csv` header:
  - `priority_number,priority_date,document_category,das_code,osf_ack_id,downloaded`

---

## Authentication (OAuth2 Client Assertion)

- Class: `JwtAssertionGenerator` + `GetToken`
- Flow:
  1. Load EC private key from `pemFile`.
  2. Build ES256-signed JWT with claims:
     - `iss` = `clientId`, `sub` = `clientId`, `aud` = `audience`, `exp` = now + ~1000s.
  3. Exchange for access token via `POST {issuer}/access_token`:
     - Content-Type: `application/x-www-form-urlencoded`
     - Params: `grant_type=client_credentials`, `scope`, `client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer`, `client_assertion` = signed JWT.
  4. Parse response JSON for `access_token` and `expires_in`.

---

## Transactions

Below are the API transactions implemented by the sample, with the request/response data used in code.

### 1) Obtain File ID and Upload URL

- Class: `ObtainFileIdAndUploadUrl`
- Endpoint: `POST {das}/files/url-uploads`
- Headers: `Authorization: Bearer <token>`, `Content-Type: application/json`, `Accept: application/json`
- Body:
  - `{ "fileReference": "<name>", "fileFormatCategory": "pdf", "fileChecksum": "<sha256>" }`
- Success response fields:
  - `fileId`, `fileUploadUrl`

Helpers:

- Checksum: `ObtainFileIdAndUploadUrl.getFileChecksum(filePath)` computes `SHA-256` hex.
- Reference: `ObtainFileIdAndUploadUrl.getFileReference(filePath)` derives a reference from filename.

### 2) Upload File

- Class: `UploadFileToDas`
- Endpoint: `PUT {fileUploadUrl}` (pre-signed, no auth header)
- Headers: `Content-Type: application/pdf`
- Body: Raw PDF bytes
- Response: HTTP 200 on success

If upload URL is expired:

- Class: `GetUpdatedUploadUrl`
- Endpoint: `PUT {das}/files/url-uploads`
- Headers: `Authorization: Bearer <token>`, `Content-Type: application/json`
- Body: `{ "fileId": "<fileId>" }`
- Response: `fileUploadUrl` (new)

### 3) Check File Status

- Class: `CheckFileStatus`
- Endpoint: `GET {das}/files?fileId=<fileId>`
- Headers: `Authorization: Bearer <token>`
- Response handling:
  - Looks for `fileSizeQuantity` and `fileStatusCategory`.
  - Polls every 5s until recognized as `ACCEPTED` or `REJECTED`.

### 4) Register File (Registration)

- Class: `RegisterFile`
- Endpoint: `POST {das}/registrations`
- Headers: `Authorization: Bearer <token>`, `Content-Type: application/json`
- Body:
```
{
  "operationCategory": "registration",
  "documentKindCategory": "<document_category>",
  "documentNumber": "<priority_number>",
  "documentDate": "<priority_date>",
  "dasAccessCode": "<das_code>",
  "applicationCategory": "<application_category>",
  "applicationNumber": "<application_number>",
  "applicationFilingDate": "<application_date>",
  "email": null,
  "fileId": "<fileId>"
}
```
- Success response fields:
  - `requestAckId` (stored as `ack_id` in the registration CSV)

### 5) Download Registration Certificate

- Class: `GetCertificateFromDas`
- Endpoint: `GET {das}/registrations/certificates`
- Headers: `Authorization: Bearer <token>`, `Content-Type: application/pdf`
- Query params:
  - `documentKindCategory`, `documentNumber`, `documentDate`, `dasAccessCode`
- Behavior: Writes PDF to `<localFolder>/certificate_<number>_<date>.pdf`

### 6) Register Retrieval (OSF Ack Id)

- Class: `GetOsfAckId`
- Endpoint: `POST {das}/retrievals`
- Headers: `Authorization: Bearer <token>`, `Content-Type: application/json`
- Body:
```
{
  "operationCategory": "retrieval",
  "documentKindCategory": "<document_category>",
  "documentNumber": "<priority_number>",
  "documentDate": "<priority_date>",
  "dasAccessCode": "<das_code>",
  "applicationCategory": null,
  "applicationNumber": null,
  "applicationFilingDate": null
}
```
- Success response fields:
  - `requestAckId` (stored as `osf_ack_id` in the retrieval CSV)

### 7) Get File Download URL and Download

- Class: `GetFileFromDas`
- Endpoint: `POST {das}/files/url-downloads`
- Headers: `Authorization: Bearer <token>`, `Content-Type: application/json`
- Body:
```
{
  "documentKindCategory": "<document_category>",
  "documentNumber": "<priority_number>",
  "documentDate": "<priority_date>",
  "osfAckId": "<requestAckId>"
}
```
- Success response fields:
  - `fileDownloadUrl`
- Download: `RetrievalTest.downloadFile(url, prefix)` streams the PDF to `localFolder/<prefix>.pdf`

---

## End-to-End Flows

### Registration Flow (RegistrationTest)

For each row in `registration_test.csv` where `registered != true`:

1. Obtain OAuth access token.
2. Compute file SHA-256.
3. POST `/files/url-uploads` → get `fileId`, `fileUploadUrl`.
4. PUT to `fileUploadUrl` with PDF.
5. Poll GET `/files?fileId=...` until `ACCEPTED`.
6. POST `/registrations` → get `requestAckId`.
7. Update CSV with `file_id`, `registered=true`, `ack_id`.

### Retrieval Flow (RetrievalTest)

For each row in `retrieval_test.csv` where `downloaded != true`:

1. Obtain OAuth access token.
2. If no `osf_ack_id`, POST `/retrievals` → save `requestAckId`.
3. POST `/files/url-downloads` → get `fileDownloadUrl`.
4. Download PDF to `localFolder`.
5. Update CSV with `downloaded=true`.

### Certificate Download (CertificateDownloadTest)

For each row in `registration_test.csv` where `registered == true`:

1. Obtain OAuth access token.
2. GET `/registrations/certificates` with query params.
3. Save certificate PDF to `localFolder`.

---

## Running Without Scripts

- Build: `mvn -q clean package`
- Run registration: `java -cp ./target/restapitest-1.0-SNAPSHOT.jar:./target/lib/* org.wipo.das.restapitest.RegistrationTest config/config.properties config/registration_test.csv`
- Run retrieval: `java -cp ./target/restapitest-1.0-SNAPSHOT.jar:./target/lib/* org.wipo.das.restapitest.RetrievalTest config/config.properties config/retrieval_test.csv`
- Run certificates: `java -cp ./target/restapitest-1.0-SNAPSHOT.jar:./target/lib/* org.wipo.das.restapitest.CertificateDownloadTest config/config.properties config/registration_test.csv`

---

## Troubleshooting

- Auth failures:
  - Verify `pemFile` path and format (EC key), `clientId`, `issuer`, `audience`, and `scope`.
  - Inspect logs in `logs/` and the console for OAuth error details.

- Upload URL expired / 403:
  - Flow will attempt `GetUpdatedUploadUrl`. If it continues failing, refresh the token and re-request a new upload URL.

- File status stuck in processing:
  - Ensure upload succeeded (HTTP 200). Check server-side processing delays or contact support if persistent.

- CSV not updating:
  - Ensure the process has write permission and the CSV file is not open in another program.

---

## Security Notes

- Keep your private key (`pemFile`) secure; do not commit keys to source control.
- Treat access tokens and pre-signed URLs as sensitive. They grant time-limited access to resources.
- Rotate keys and credentials according to your organization’s policies.

---

## License

Sample code is provided for integration guidance. Refer to your organization’s licensing requirements for distribution or reuse.

---

## Diagrams

- Registration flow: `docs/diagrams/registration_flow.puml` (PNG/SVG rendered alongside)
- Retrieval flow: `docs/diagrams/retrieval_flow.puml` (PNG/SVG rendered alongside)
- Certificate download flow: `docs/diagrams/certificate_download_flow.puml` (PNG/SVG rendered alongside)

Rendered images are available as `.png` and `.svg` in the same folder.
