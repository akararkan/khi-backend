package ak.dev.khi_backend.user.jwt;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Token {
    private String token;
    private String response;
    public Token(String response){
        this.response = response;
    }
}