# SkillSync Backend

Spring Boot backend for **SkillSync / Automated Talent Skill-Validator**, a multi-tenant technical assessment platform for recruiters, admins, and candidates.

The backend provides authentication, organization-scoped assessment management, candidate assignment workflows, secure Docker-based code execution, email verification, password reset, role-aware Google OAuth login, and deployment support for AWS EC2.

---

## Overview

SkillSync helps organizations validate technical talent through structured quizzes and coding challenges.

Admins and recruiters can:

- Register an organization workspace
- Verify their email
- Log in with email/password or Google OAuth after an admin account exists
- Invite candidates
- Create quiz and coding assessments
- Assign assessments to candidates
- View candidates, assessments, assignments, and results
- Execute submitted code through Docker
- Manually grade submissions and provide feedback
- Manage profile information

Candidates can:

- Register and verify their email
- Log in with email/password or Google OAuth
- Access assignments across linked organizations
- Run coding challenge code before final submission
- Submit quiz answers or code
- View score, feedback, execution output, and errors after grading
- Manage profile information

---

## Tech Stack

- Java 17
- Spring Boot 3.5.x
- Maven
- MongoDB Atlas
- Spring Security
- JWT authentication
- Google OAuth2 client
- Spring Mail SMTP
- Docker CLI based code execution
- AWS EC2 deployment
- systemd service
- GitHub Actions CI/CD

---

## Main Features

### Authentication and Security

- JWT-based authentication
- Role-based access control
- Admin and candidate roles
- Strong password validation
- Email normalization
- Generic login failure messages
- Email verification after signup
- Resend verification email with cooldown
- Forgot password and reset password flow
- Role-aware Google OAuth login
- Existing admins can log in with Google when the Google email matches their existing admin account
- Existing candidates can log in with Google when the Google email matches their account
- New Google users are created as candidates only
- Google OAuth does not create new admin organizations
- CORS configuration through environment variables

### Multi-Tenant Organization Scope

- Admins belong to one organization
- Candidates can be linked to multiple organization-specific candidate profiles
- Admin data access is scoped by organization
- Candidate dashboard aggregates assignments from linked candidate profiles

### Assessments and Assignments

- Create quiz assessments
- Create coding challenge assessments
- Assign assessments to candidates
- Candidate submission workflow
- Admin grading workflow
- Candidate result visibility after grading

### Code Execution

Supported languages:

- Java
- JavaScript
- Python

Execution security controls:

- Docker-based execution
- Network disabled
- Memory limit
- CPU limit
- PID limit
- Timeout
- Max source size
- Max output size
- Language whitelist
- Temporary directory cleanup

---

## Project Structure

```txt
src/main/java/app/SkillSync
├── config
├── controller
├── dto
├── exception
├── model
├── repository
├── security
└── service
```

Important areas:

```txt
controller/
  AuthController.java
  HealthController.java
  ProfileController.java
  AssessmentController.java
  CandidateController.java

service/
  AuthService.java
  ProfileService.java
  AssessmentService.java
  CandidateService.java
  CodeExecutionService.java
  EmailTokenService.java
  MailService.java
  OAuthLoginService.java

security/
  JwtAuthenticationFilter.java
  JwtService.java
  OAuth2LoginSuccessHandler.java
  OAuth2LoginFailureHandler.java
```

---

## Environment Configuration

The committed `application.properties` should contain placeholders only. Real secrets must come from `.env`, GitHub Secrets, or EC2 environment files.

### Local `.env`

Create this file in the backend root:

```txt
SkillSync-backend/.env
```

Example:

```env
PORT=8080

MONGODB_URI=mongodb+srv://YOUR_VALUE
SPRING_DATA_MONGODB_URI=mongodb+srv://YOUR_VALUE

JWT_SECRET=YOUR_LONG_RANDOM_SECRET
JWT_EXPIRATION_MS=86400000

CODE_EXECUTION_ENABLED=true
CODE_EXECUTION_TIMEOUT_SECONDS=8
CODE_EXECUTION_MAX_SOURCE_SIZE_CHARS=20000
CODE_EXECUTION_MAX_OUTPUT_SIZE_CHARS=5000

CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000

GOOGLE_CLIENT_ID=YOUR_GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET=YOUR_GOOGLE_CLIENT_SECRET
OAUTH_FRONTEND_SUCCESS_URL=http://localhost:3000/oauth-success
OAUTH_FRONTEND_FAILURE_URL=http://localhost:3000/login?oauthError=true

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your_email@gmail.com
MAIL_PASSWORD=your_google_app_password
APP_EMAIL_FROM=your_email@gmail.com

FRONTEND_BASE_URL=http://localhost:3000
EMAIL_VERIFICATION_EXPIRATION_MINUTES=1440
PASSWORD_RESET_EXPIRATION_MINUTES=30
RESEND_VERIFICATION_COOLDOWN_SECONDS=60
```

Never commit `.env`.

---

## Required Environment Variables

| Variable | Purpose |
|---|---|
| `PORT` | Backend server port |
| `MONGODB_URI` | MongoDB Atlas connection string |
| `SPRING_DATA_MONGODB_URI` | MongoDB URI used by Spring Data |
| `JWT_SECRET` | JWT signing secret |
| `JWT_EXPIRATION_MS` | JWT expiry in milliseconds |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins |
| `CODE_EXECUTION_ENABLED` | Enables/disables Docker code execution |
| `CODE_EXECUTION_TIMEOUT_SECONDS` | Code execution timeout |
| `CODE_EXECUTION_MAX_SOURCE_SIZE_CHARS` | Max source input size |
| `CODE_EXECUTION_MAX_OUTPUT_SIZE_CHARS` | Max captured output size |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `OAUTH_FRONTEND_SUCCESS_URL` | Frontend OAuth success redirect |
| `OAUTH_FRONTEND_FAILURE_URL` | Frontend OAuth failure redirect |
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP app password |
| `APP_EMAIL_FROM` | Sender email |
| `FRONTEND_BASE_URL` | Frontend base URL used in verification/reset links |
| `EMAIL_VERIFICATION_EXPIRATION_MINUTES` | Email verification token lifetime |
| `PASSWORD_RESET_EXPIRATION_MINUTES` | Password reset token lifetime |
| `RESEND_VERIFICATION_COOLDOWN_SECONDS` | Verification resend cooldown |

---

## Local Development

### Prerequisites

- Java 17
- Maven
- Docker Desktop
- MongoDB Atlas database
- Gmail app password or another SMTP provider for email testing

### Run tests

```bash
mvn clean test
```

### Run backend

```bash
mvn spring-boot:run
```

Backend will run at:

```txt
http://localhost:8080
```

Health check:

```bash
curl http://localhost:8080/api/health
```

---

## Google OAuth Setup

In Google Cloud Console:

1. Create OAuth consent screen
2. Create OAuth client ID
3. Application type: Web application
4. Add redirect URIs

Local redirect URI:

```txt
http://localhost:8080/login/oauth2/code/google
```

UAT API Gateway redirect URI:

```txt
https://<api-gateway-id>.execute-api.ap-southeast-1.amazonaws.com/login/oauth2/code/google
```

Authorized JavaScript origins:

```txt
http://localhost:3000
https://your-vercel-app.vercel.app
```

### Google OAuth Behavior

SkillSync supports role-aware Google OAuth:

```txt
Existing ADMIN by Google email      → login allowed → /admin
Existing CANDIDATE by Google email  → login allowed → /candidate
New Google user                     → created as CANDIDATE → /candidate
```

Google OAuth does **not** create new admin organizations. New admin organization registration must still happen through the standard email/password registration flow.

This prevents random Google users from creating recruiter/admin workspaces while still allowing existing admins to use Google sign-in.

---

## Email Verification and Password Reset

Signup flow:

```txt
Register
→ User created as emailVerified=false
→ Verification email sent
→ User verifies email
→ Login allowed
```

Forgot password flow:

```txt
Forgot password request
→ Generic success response
→ Reset email sent if account exists
→ User resets password
→ Login with new password
```

Security notes:

- Reset and verification tokens are stored as hashes
- Forgot password does not reveal whether an email exists
- Verification resend has a cooldown
- SMTP failures should not expose secrets to users

---

## API Overview

### Auth

```txt
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/verify-email?token=...
POST /api/auth/resend-verification
POST /api/auth/forgot-password
POST /api/auth/reset-password
```

### Profile

```txt
GET   /api/profile
PATCH /api/profile
PATCH /api/profile/password
```

### Candidates

```txt
GET  /api/candidates
POST /api/candidates
```

### Assessments and Assignments

```txt
GET   /api/assessments
POST  /api/assessments
POST  /api/assessments/assign
GET   /api/assessments/assignments
GET   /api/assessments/my-assignments
POST  /api/assessments/assignments/{assignmentId}/run
POST  /api/assessments/assignments/{assignmentId}/submit
POST  /api/assessments/assignments/{assignmentId}/execute
PATCH /api/assessments/assignments/{assignmentId}/grade
```

---

## Deployment Architecture

Current UAT deployment:

```txt
Vercel frontend
→ API Gateway HTTPS proxy
→ EC2 Spring Boot backend on port 8080
→ MongoDB Atlas
→ Docker on EC2 for code execution
```

Backend runs on EC2 using systemd:

```txt
skillsync-backend.service
```

Useful EC2 commands:

```bash
sudo systemctl status skillsync-backend --no-pager -l
sudo systemctl restart skillsync-backend
sudo journalctl -u skillsync-backend -f
```

---

## GitHub Actions CI/CD

Backend CI/CD should:

1. Checkout repo
2. Set up Java 17
3. Run tests
4. Build JAR
5. SSH to EC2
6. Pull `uat`
7. Write `.env` from GitHub Secrets
8. Build on EC2
9. Restart systemd service
10. Check service status

Deployment branch:

```txt
uat
```

Stable branch:

```txt
main
```

---

## UAT Smoke Test Checklist

After backend deployment:

```txt
1. Health endpoint returns UP
2. Candidate registration sends verification email
3. Admin registration sends verification email
4. Unverified login is blocked
5. Resend verification email works and cooldown applies
6. Email verification succeeds
7. Login succeeds after verification
8. Forgot password sends reset email
9. Password reset succeeds
10. Google OAuth candidate login works
11. Existing admin Google OAuth login works
12. New Google OAuth users are created as candidates only
13. Admin can invite candidate
14. Admin can create assessment
15. Admin can assign assessment
16. Candidate sees assigned assessment
17. Candidate can run code before submit
18. Candidate can submit answer/code
19. Admin can execute Docker grading
20. Admin can manually grade
21. Candidate sees result and feedback
22. Candidate never sees expected output
```

---

## Security Notes

- Do not commit `.env`
- Do not commit `.pem`, `.key`, or secrets
- Rotate MongoDB and JWT secrets if exposed
- Keep JWT secret long and random
- Keep Google OAuth secret in GitHub Secrets and EC2 `.env`
- Use Google app password for Gmail SMTP
- Avoid opening EC2 port 8080 publicly long-term
- Add AWS budget alerts
- Harden SSH access later using SSM, restricted IPs, or a self-hosted runner
- Replace OAuth token-in-query redirect with a one-time exchange code before production

---

## Suggested Future Enhancements

- AI assessment generator
- AI-assisted grading feedback suggestions
- Audit logs
- Multiple coding test cases
- Monaco code editor
- Email invite templates
- Admin analytics endpoint
- Domain and production-grade HTTPS networking
- Refresh tokens
- Verified email change flow
- Organization admin invite flow
