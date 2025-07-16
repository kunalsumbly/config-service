# Config Service with App-Specific Encryption

This Spring Cloud Config Server implementation provides enhanced encryption/decryption capabilities with app-specific and profile-specific encryption keys stored in AWS Secrets Manager.

## Features

- **App-Specific Encryption Keys**: Each application and profile combination can have its own encryption key and salt
- **Key Rotation**: Encryption keys can be rotated for specific applications and profiles without affecting others
- **AWS Secrets Manager Integration**: Keys are securely stored in AWS Secrets Manager
- **RESTful API**: Simple API for creating keys, encrypting, and decrypting values

## API Endpoints

### Create Encryption Keys

```
POST /create-encryption-keys/{appName}/{profile}
```

Creates or updates encryption keys for a specific application and profile. If keys already exist, they will be overwritten.

**Path Parameters:**
- `appName`: The application name (e.g., "catalog-service")
- `profile`: The profile name (e.g., "local", "prod")

**Response:**
```json
{
  "status": "success",
  "message": "Encryption keys created successfully for catalog-service/local"
}
```

### Encrypt Value

```
POST /encrypt/{appName}/{profile}
```

Encrypts a value using app-specific and profile-specific encryption keys.

**Path Parameters:**
- `appName`: The application name (e.g., "catalog-service")
- `profile`: The profile name (e.g., "local", "prod")

**Request Body:**
```json
{
  "value": "plaintext-to-encrypt"
}
```

**Response:**
```json
{
  "status": "success",
  "encrypted": "encrypted-value"
}
```

### Decrypt Value

```
POST /decrypt/{appName}/{profile}
```

Decrypts a value using app-specific and profile-specific encryption keys.

**Path Parameters:**
- `appName`: The application name (e.g., "catalog-service")
- `profile`: The profile name (e.g., "local", "prod")

**Request Body:**
```json
{
  "value": "encrypted-value"
}
```

**Response:**
```json
{
  "status": "success",
  "decrypted": "decrypted-value"
}
```

### Add Secret

```
POST /add-secret/{appName}/{profile}
```

Encrypts a value and adds it to the shell secret container.

**Path Parameters:**
- `appName`: The application name (e.g., "catalog-service")
- `profile`: The profile name (e.g., "local", "prod")

**Request Body:**
```json
{
  "key": "spring.data.redis.password",
  "value": "plaintext-password-to-encrypt"
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Secret added successfully for catalog-service/local"
}
```

## AWS Secrets Manager Structure

Encryption keys, salts, and application secrets are stored in AWS Secrets Manager with the following path structure:

```
/config-secrets/{appName}/{profile}/encrypt-key    # Encryption key for the application and profile
/config-secrets/{appName}/{profile}/encrypt-salt   # Encryption salt for the application and profile
/config-secrets/{appName}/{profile}                # Empty shell secret for storing application secrets
```

For example:
```
/config-secrets/catalog-service/local/encrypt-key
/config-secrets/catalog-service/local/encrypt-salt
/config-secrets/catalog-service/local              # Empty shell secret initialized with {}
```

The empty shell secret at `/config-secrets/{appName}/{profile}` is created automatically when generating encryption keys. This serves as a container where you can manually add encrypted configuration values after using the `/encrypt` endpoint.

The Config Server uses a custom `TextEncryptorLocator` implementation that retrieves the appropriate encryption keys based on the application name and profile when processing encryption/decryption requests.

## Key Rotation and Password Management

### Encryption Key Rotation Process

Rotating encryption keys is a critical security practice that should be performed periodically. This process allows you to change the encryption keys (encrypt.key and encrypt.salt) without affecting other applications or profiles.

#### Detailed Steps for Rotating Encryption Keys:

1. **Generate New Encryption Keys**:
   ```bash
   curl -X POST http://localhost:8888/create-encryption-keys/catalog-service/local
   ```
   This will create new encrypt.key and encrypt.salt values in AWS Secrets Manager, overwriting the existing ones.

2. **Identify All Encrypted Values**:
   Before rotating keys, identify all values that were encrypted with the old keys. These may include:
   - Values in the shell secret container at `/config-secrets/catalog-service/local`
   - Values in configuration files or other storage locations
   - Values in environment variables or deployment configurations

3. **Re-encrypt All Sensitive Values**:
   For each identified value, decrypt it using the old keys (if you still have access to them) and re-encrypt it with the new keys:
   ```bash
   # First, decrypt the value using the old keys (if possible)
   # Then, encrypt with the new keys
   curl -X POST http://localhost:8888/encrypt/catalog-service/local \
     -H "Content-Type: application/json" \
     -d '{"value": "decrypted-value"}'
   ```

4. **Update All Configuration Sources**:
   Replace all encrypted values in your configuration sources with the newly encrypted values.

5. **Update Shell Secret Container**:
   If you're using the shell secret container, you can update each secret:
   ```bash
   curl -X POST http://localhost:8888/add-secret/catalog-service/local \
     -H "Content-Type: application/json" \
     -d '{"key": "spring.data.redis.password", "value": "decrypted-redis-password"}'
   ```

6. **Verify Configuration**:
   Test your application with the new encrypted values to ensure everything works correctly.

#### Impact and Considerations:

- **Downtime Risk**: Applications will fail to start or function correctly if they try to use values encrypted with old keys after the keys have been rotated.
- **Staged Rotation**: For critical systems, consider rotating keys in stages (e.g., one service or profile at a time).
- **Backup**: Always back up your old encryption keys before rotation in case you need to recover data.
- **Documentation**: Keep a record of when key rotations occur and which systems are affected.

### Application Password Rotation

Rotating application passwords (like Redis password) is separate from rotating encryption keys. This process allows you to change the actual passwords used by your applications while minimizing downtime.

#### Detailed Steps for Rotating Application Passwords (e.g., Redis):

1. **Create New Credentials**:
   First, create new credentials in the target system (e.g., create a new Redis user with the same permissions).

2. **Encrypt the New Password**:
   ```bash
   curl -X POST http://localhost:8888/encrypt/catalog-service/local \
     -H "Content-Type: application/json" \
     -d '{"value": "new-redis-password"}'
   ```

3. **Add the New Password to Configuration**:
   Add the new encrypted password to your configuration, using the `/add-secret` endpoint if you're using the shell secret container:
   ```bash
   curl -X POST http://localhost:8888/add-secret/catalog-service/local \
     -H "Content-Type: application/json" \
     -d '{"key": "spring.data.redis.password", "value": "new-redis-password"}'
   ```

4. **Update Application Configuration**:
   Update your application's configuration to use the new password. If your application supports it, you can use Spring's `@RefreshScope` and the `/actuator/refresh` endpoint to reload the configuration without restarting.

5. **Verify New Credentials**:
   Test that your application can connect to the service with the new credentials.

6. **Remove Old Credentials**:
   Once all instances are using the new credentials, remove the old credentials from the target system.

#### Zero-Downtime Password Rotation Strategy:

For critical systems where downtime is not acceptable, follow these additional steps:

1. **Dual Credential Period**:
   Configure your target system (e.g., Redis) to accept both old and new credentials for a transition period.

2. **Rolling Updates**:
   Update application instances one by one to use the new credentials, allowing time to verify each instance works correctly.

3. **Monitoring**:
   Monitor connection attempts with old credentials to ensure all instances have transitioned to the new credentials.

4. **Credential Cleanup**:
   Only after confirming all instances are using the new credentials, remove the old credentials.

## Example Usage

### Creating Encryption Keys

```bash
curl -X POST http://localhost:8888/create-encryption-keys/catalog-service/local
```

### Encrypting a Value

```bash
curl -X POST http://localhost:8888/encrypt/catalog-service/local \
  -H "Content-Type: application/json" \
  -d '{"value": "redis-password"}'
```

### Decrypting a Value

```bash
curl -X POST http://localhost:8888/decrypt/catalog-service/local \
  -H "Content-Type: application/json" \
  -d '{"value": "encrypted-value"}'
```

### Adding a Secret

```bash
curl -X POST http://localhost:8888/add-secret/catalog-service/local \
  -H "Content-Type: application/json" \
  -d '{"key": "spring.data.redis.password", "value": "redis-password"}'
```

## Configuration

The base path for storing secrets in AWS Secrets Manager can be configured in `application.yml`:

```yaml
config.server.sm.base.path: /config-secrets (default value)
```
