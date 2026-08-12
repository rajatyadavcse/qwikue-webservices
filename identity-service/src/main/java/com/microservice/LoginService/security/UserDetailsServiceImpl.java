package com.microservice.LoginService.security;

import com.microservice.LoginService.entity.User;
import com.microservice.LoginService.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Spring Security calls this with the identifier passed to UsernamePasswordAuthenticationToken.
     * Identifier can be either the user's email address or a 10-digit phone number.
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("Identifier cannot be empty");
        }
        String cleanIdentifier = identifier.trim();

        User user;
        if (cleanIdentifier.matches("^[0-9]{10}$")) {
            user = userRepository.findByPhone(cleanIdentifier)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + cleanIdentifier));
        } else {
            user = userRepository.findByEmail(cleanIdentifier)
                    .or(() -> userRepository.findByEmail(cleanIdentifier.toLowerCase()))
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + cleanIdentifier));
        }
        return new UserPrincipal(user);
    }
}
