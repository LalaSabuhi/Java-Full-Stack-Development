package com.dailycodework.sbend2endapplication.registration;

import com.dailycodework.sbend2endapplication.event.RegistrationCompleteEvent;
import com.dailycodework.sbend2endapplication.event.listener.RegistrationCompleteEventListener;
import com.dailycodework.sbend2endapplication.registration.password.PasswordResetTokenService;
import com.dailycodework.sbend2endapplication.registration.token.VerificationToken;
import com.dailycodework.sbend2endapplication.registration.token.VerificationTokenRepository;
import com.dailycodework.sbend2endapplication.registration.token.VerificationTokenService;
import com.dailycodework.sbend2endapplication.user.IUserService;
import com.dailycodework.sbend2endapplication.user.User;
import com.dailycodework.sbend2endapplication.utility.UrlUtil;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.UUID;

/**
 * @author Sampson Alfred
 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/registration")
public class RegistrationController {
    private final IUserService userService;
    private final ApplicationEventPublisher publisher;
    private final VerificationTokenService tokenService;
    private final PasswordResetTokenService passwordResetTokenService;
    private final RegistrationCompleteEventListener eventListener;

    @GetMapping("/registration-form")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new RegistrationRequest());
        return "registration";
    }
    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") RegistrationRequest registration, HttpServletRequest request) {
        User user = userService.registerUser(registration);
        publisher.publishEvent(new RegistrationCompleteEvent(user, UrlUtil.getApplicationUrl(request)));
        return "redirect:/registration/registration-form?success";
    }
    @GetMapping("/verifyEmail")
    public String verifyEmail(@RequestParam("token") String token) {
        Optional<VerificationToken> theToken = tokenService.findByToken(token);
        if (theToken.isPresent() && theToken.get().getUser().isEnabled()) {
            return "redirect:/login?verified";
        }
        String verificationResult = tokenService.validateToken(token);
        switch (verificationResult.toLowerCase()) {
            case "expired":
                return "redirect:/error?expired";
            case "valid":
                return "redirect:/login?valid";
            default:
                return "redirect:/error?invalid";
        }
    }
 //=========================== Password reset zone ========================================
    @GetMapping("/forgot-password-request")
    public String forgotPassword(){
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(HttpServletRequest request, Model model){
        String email = request.getParameter("email");
        User user = userService.findByEmail(email);
        if (user == null){
            return "redirect:/registration/forgot-password-request?not_found";
        }
        String passwordResetToken = UUID.randomUUID().toString();
        passwordResetTokenService.createPasswordResetTokenForUser(user,passwordResetToken);
        //send password reset verification email to the user
        String url = UrlUtil.getApplicationUrl(request)+"/registration/password-reset-form?token="+passwordResetToken;
        try {
            eventListener.sendPasswordResetVerificationEmail(url);
        } catch (MessagingException | UnsupportedEncodingException  e) {
            model.addAttribute("error", e.getMessage());
        }
        return "redirect:/registration/forgot-password-request?success";
    }

    @GetMapping("/password-reset-form")
    public String passwordResetForm(@RequestParam("token") String token, Model model){
        model.addAttribute("token", token);
        return "reset-password";
    }
    @PostMapping("/reset-password")
    public String resetPassword(HttpServletRequest request){
        String token = request.getParameter("token");
        String newPassword = request.getParameter("password");
        String tokenVerificationResult = passwordResetTokenService.validatePasswordResetToken(token);
        if (!tokenVerificationResult.equalsIgnoreCase("valid")){
            return "redirect:/error?invalid_token";
        }
        Optional<User> theUser = passwordResetTokenService.findUserByToken(token);
        if (theUser.isPresent()){
            passwordResetTokenService.resetPassword(theUser.get(), newPassword);
            return "redirect:/login?reset_success";
        }
        return "redirect:/error?not_found";
    }

    //=========================== End password reset zone ========================================
}
