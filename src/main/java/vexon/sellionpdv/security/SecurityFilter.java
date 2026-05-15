package vexon.sellionpdv.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vexon.sellionpdv.tenant.TenantContext;
import vexon.sellionpdv.usuario.UsuarioRepository;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {
    private final TokenService tokenService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var token = this.recoverToken(request);

        try {
            if (token != null) {
                Long tenantId = tokenService.extrairTenantId(token);
                String email = tokenService.validarToken(token);

                if (tenantId != null && email != null) {
                    TenantContext.setCurrentTenant(tenantId);

                    var usuario = usuarioRepository.findByEmailWithTenant(email);

                    if (usuario.isPresent()) {
                        var userDetails = org.springframework.security.core.userdetails.User
                                .withUsername(usuario.get().getEmail())
                                .password(usuario.get().getSenhaHash())
                                .roles(usuario.get().getRole().replace("ROLE_", ""))
                                .build();

                        var authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            TenantContext.clear();
        }
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null ) return null;
        return authHeader.replace("Bearer ", "");
    }
}