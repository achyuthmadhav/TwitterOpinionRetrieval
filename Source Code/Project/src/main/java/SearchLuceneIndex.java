import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;

import java.io.*;

import org.apache.lucene.queryparser.classic.ParseException;

import java.util.List;

@Path("/search")
public class SearchLuceneIndex {

    @GET
    @Path("/lucene/{keyword}")
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response searchLucene(@PathParam("keyword")String keyword) {

        LuceneIndexing indexSearcher = new LuceneIndexing();
        //indexSearcher.testWriteIndex();
        try {
            APIResponse result = indexSearcher.testQueryLucene(keyword);
            return javax.ws.rs.core.Response.status(200).header("Access-Control-Allow-Origin", "*").entity(result).build();

        } catch (IOException io) {
            return null;
        } catch (ParseException pe) {
            return null;
        }
        catch (NullPointerException e){
            return null;
        }
    }


    @GET
    @Path("/test")
    @Produces(MediaType.TEXT_PLAIN)

    public String test() {
        return "hello world";
    }


    @GET
    @Path("/hadoop/{keyword}")
    @Produces(MediaType.APPLICATION_JSON)

    public javax.ws.rs.core.Response getDashBoard(@PathParam("keyword")String keyword) {

        LuceneIndexing indexSearcher = new LuceneIndexing();
        APIResponse result = indexSearcher.testQueryHadoop(keyword);
        return javax.ws.rs.core.Response.status(200).header("Access-Control-Allow-Origin", "*").entity(result).build();
    }

}