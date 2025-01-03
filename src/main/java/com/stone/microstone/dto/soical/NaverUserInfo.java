package com.stone.microstone.dto.soical;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NaverUserInfo {

    @JsonProperty("response")
    private Response response;

    @Getter
    @Setter
    private static class Response{
        private String email;
        private String name;
    }


    public String getEmail() {
        return response != null ? response.getEmail() : null;
    }
    public String getName() {return response != null ? response.getName() : null;}
}
