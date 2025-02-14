package com.codefolio.backend.config;

import com.codefolio.backend.user.UserRepository;
import com.codefolio.backend.user.UserSession;
import com.codefolio.backend.user.UserSessionRepository;
import com.codefolio.backend.user.Users;
import com.codefolio.backend.user.repository.GithubRepo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
@AllArgsConstructor
public class CustomAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String email;
        String name;
        String username;
        String company;
        String location;

        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            OAuth2AuthorizedClient oAuthClient = authorizedClientService.loadAuthorizedClient(
                    oauthToken.getAuthorizedClientRegistrationId(),
                    oauthToken.getName());

            String accessToken = oAuthClient.getAccessToken().getTokenValue();
            email = ((OAuth2AuthenticationToken) authentication).getPrincipal().getAttribute("email");
            name = ((OAuth2AuthenticationToken) authentication).getPrincipal().getAttribute("name");
            username = ((OAuth2AuthenticationToken) authentication).getPrincipal().getAttribute("login");
            company = ((OAuth2AuthenticationToken) authentication).getPrincipal().getAttribute("company");
            location = ((OAuth2AuthenticationToken) authentication).getPrincipal().getAttribute("location");

            System.out.println(email);
            System.out.println(name);
            System.out.println(username);
            System.out.println(company);
            System.out.println(location);

            try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest repoRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/users/" + username + "/repos"))
                    .header("Authorization", "token " + accessToken)
                    .build();


                HttpResponse<String> repoResponse = client.send(repoRequest, HttpResponse.BodyHandlers.ofString());
                Gson gson = new Gson();
                Type repoListType = new TypeToken<ArrayList<GithubRepo>>(){}.getType();
                List<GithubRepo> repos = gson.fromJson(repoResponse.body(), repoListType);

                for(GithubRepo repo : repos) {
                    System.out.println("Repository name: " + repo.getName());
                    System.out.println("Owner's login: " + repo.getOwner().getLogin());
                    System.out.println("Language: " + repo.getLanguage());
                    System.out.println("Last updated: " + repo.getUpdatedAt());
                    System.out.println("Description: " + repo.getDescription());
                }

            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }


        } else if (authentication instanceof UsernamePasswordAuthenticationToken) {
            email = authentication.getName();
            name = "";
        } else {
            throw new IllegalArgumentException("Unexpected type of authentication: " + authentication);
        }

        System.out.println("User authenticated with email: " + email);
        Users user;
        if (userRepository.findByEmail(email).isPresent()){
            user = userRepository.findByEmail(email).get();
        }else if (authentication instanceof OAuth2AuthenticationToken){
            String randomPassword = UUID.randomUUID().toString();
            user = new Users(name, email, passwordEncoder.encode(randomPassword));
            userRepository.save(user);
        }
        else {
            throw new IllegalArgumentException("User not found");
        }

        String sessionId = RequestContextHolder.currentRequestAttributes().getSessionId();

        UserSession userSession = new UserSession(sessionId, user, new Date());

        userSessionRepository.save(userSession);

        Cookie cookie = new Cookie("SESSION_ID", sessionId);
        cookie.setPath("/");
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        System.out.println("User session saved: " + userSession.getId());

        if (authentication instanceof OAuth2AuthenticationToken){
            response.sendRedirect("http://localhost:5173/dashboard");
        }
    }
}
