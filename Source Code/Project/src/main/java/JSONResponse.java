import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/getjson")
public class JSONResponse {

    @GET

    @Produces(MediaType.APPLICATION_JSON)

    public Test getJson() {

        Test t = new Test();

        t.setFirstName("Harish");
        t.setLastName("Gonnabattula");

        return t;
    }
}
