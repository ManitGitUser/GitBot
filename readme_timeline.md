
#### currently everything is for local dev, but aims for cloud deployment later

initialized spring boot in /backend[GitBot] folder

initialized next.js app in /client folder

used shadcn for ui theme using command

put up docker-compose.yaml for postgres and pgvector container

declared an incomplete Dockerfile for future dockerization for deployement (currently only local)

setup /docker folder for postgres init-extensions on first startup

wanted to set up /backend/src/main/resources/db/migration folder for flyway but couldn't

configured openai in application.yaml

used shadcn for pre-written code for /client/components/providers/theme-provider.tsx
used the same for /client/app/layout.tsx & /client/components/ui/mode-toggle.tsx for adding theme part

used tanstack for /client/components/providers/query-provider.tsx

created packages in /backend/main/java/com.example.gitbot/ -> controller, entity, exception, repository, service

setup backend /entity/User, /repository/UserRepository, /service/UserService.

setup backend /exception/BadRequestException, /exception/NotFoundException, /exception/UnauthorizedException, /exception/GlobalExceptionHandler

setup backend /config/SecurityConfig for securityfilterchain

setup backend /security/GitHubOAuth2UserService which implements default OAuth2UserService to load new user with upsert using /backend/security/AppUserPrinicipal which implements OAuth2User
added backend /security/CurrentUser to validate auth in SecurityContext of AppUserPrincipal

configured backend /config/CorsConfig and /config/CryptoConfig for cors config source and token encryptor

setup backend /controller/AuthController for api endpoints

created record dto for UserResponse at backend /dto/UserResponse

now on to client
created client /app/login/page.tsx for login dashboard

added client /lib/api.ts for user type and apis : user, repository, indexstatus, chatsession, citation, chatmessage, api, errors.

added client /components/icons/gitbot-icon.tsx, github-icom.tsx, language-icon.tsx
also :-
/components/layout/app-shell.tsx
/app/auth/callback/page.tsx
/app/login/page.tsx
/app/dashboard/page.tsx
/hooks/use-auth.ts
/lib/query-keys.ts