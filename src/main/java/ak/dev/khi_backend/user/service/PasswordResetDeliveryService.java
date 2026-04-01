package ak.dev.khi_backend.user.service;

import ak.dev.khi_backend.user.model.User;

public interface PasswordResetDeliveryService {

    void deliver(User user, String resetToken);
}

