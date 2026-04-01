package ak.dev.khi_backend.user.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class WelcomeAPI {

    @GetMapping("/welcome")
    public String welcome() {
        return "Welcome to HadiShop!";
    }
}
