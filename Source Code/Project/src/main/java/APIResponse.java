import java.util.List;

public class APIResponse {

    List<Response> response;

    public List<Response> getResponse() {
        return response;
    }

    public void setResponse(List<Response> response) {
        this.response = response;
    }

    public List<Response> getMostRetweeted() {
        return mostRetweeted;
    }

    public void setMostRetweeted(List<Response> mostRetweeted) {
        this.mostRetweeted = mostRetweeted;
    }

    List<Response> mostRetweeted;
}
