
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import edu.stanford.nlp.pipeline.CoreNLPProtos;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.document.Document;
import org.bson.types.ObjectId;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;


class TweetIndexing {

    String indexPath;

    IndexWriter indexWriter = null;

    MongoClient mongoClient = new MongoClient();
    MongoDatabase database;
    MongoCollection TweetsCollection;

    public TweetIndexing(String indexPath) {
        this.indexPath = indexPath;

        database = mongoClient.getDatabase("GeoTweets");
        TweetsCollection = database.getCollection("GeoTweets");

    }


    public boolean openIndex(){
        try {

            Directory dir = FSDirectory.open(Paths.get(indexPath));

            Map<String,Analyzer> analyzerPerField = new HashMap<String,Analyzer>();
            analyzerPerField.put("hashtags", new KeywordAnalyzer());
            PerFieldAnalyzerWrapper aWrapper =
                    new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
            IndexWriterConfig iwc = new IndexWriterConfig(aWrapper);

            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            indexWriter = new IndexWriter(dir, iwc);
            return true;
        } catch (Exception e) {
            System.err.println("Error opening the index. " + e.getMessage());
        }
        return false;
    }

    public void finish(){
        try {
            indexWriter.commit();
            indexWriter.close();
        } catch (IOException ex) {
            System.err.println("We had a problem closing the index: " + ex.getMessage());
        }
    }
    public void createIndex(){


        if(openIndex()){
            fetchFromCollection();
            finish();
        }

    }

    public void fetchFromCollection() {

        FieldType field = new FieldType();
        field.setStored(true);
        field.setTokenized(true);
        field.setStoreTermVectors(true);
        field.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        long i = 1;
        BasicDBObject query = new BasicDBObject();
        query.put("lang","en");
        MongoCursor<org.bson.Document> cursor = TweetsCollection.find(query).noCursorTimeout(true).iterator();


        try {
            System.out.println("Fetched records");
            while (cursor.hasNext()) {
                Document doc = new Document();
                org.bson.Document row = cursor.next();

                String body = row.getString("text");
                Field twtbody = new Field("tweetbody",body,field);
                doc.add(twtbody);

                if(row.get("entities")!=null)
                {
                    List<org.bson.Document> h = (List<org.bson.Document>)((org.bson.Document)row.get("entities")).get("hashtags");
                    if(h.size()!=0)
                    {
                        List<String> joinedHashtags = new ArrayList<String>();
                        for(org.bson.Document sd: h)
                        {
                            joinedHashtags.add("#"+sd.getString("text"));
                        }
                        Field hfield = new Field("hashtags",String.join(",",joinedHashtags),field);
                        doc.add(hfield);
                    }

                }

                org.bson.Document usr = (org.bson.Document)row.get("user");
                if (row.get("geo") != null){
                    org.bson.Document geo = (org.bson.Document)row.get("geo");
                    List<Double> coords = (List<Double>)geo.get("coordinates");
                    doc.add(new StoredField("latitude",coords.get(0)));
                    doc.add(new StoredField("longitude",coords.get(1)));
                }
                else{
                    doc.add(new StoredField("latitude",0));
                    doc.add(new StoredField("longitude",0));
                }

                doc.add(new StoredField("name",usr.getString("screen_name")));
                doc.add(new StoredField("url",usr.getString("profile_image_url")));
                doc.add(new StoredField("created",row.getString("created_at")));
                doc.add(new StoredField("twid",row.getLong("id")));
                doc.add(new StoredField("retweet_count",row.getInteger("retweet_count")));
                doc.add(new StoredField("location",usr.getString("location")));
                doc.add(new SortedNumericDocValuesField("rcount",row.getInteger("retweet_count")));
                try {
                    indexWriter.addDocument(doc);
                    if (i%10000 == 0)
                        System.out.println("Added documents "+ String.valueOf(i));

                } catch (IOException ex) {
                    System.err.println("Error adding document "+ String.valueOf(i) + " to the index. " +  ex.getMessage());
                }
                i++;
            }
        } finally {
            cursor.close();
        }



    }
}

class HadoopInvertedIndex {

    MongoClient mongoClient = new MongoClient();
    MongoDatabase database;
    MongoCollection hadoopCollection;
    MongoDatabase tdatabase;
    MongoCollection TweetsCollection;

    public HadoopInvertedIndex() {
        database = mongoClient.getDatabase("Test");
        hadoopCollection = database.getCollection("test");
        tdatabase = mongoClient.getDatabase("TweetsDB");
        TweetsCollection = tdatabase.getCollection("tweetsCollection");
    }

    public List<Response> fetchFromCollection(String keyword) {

        keyword = Stopwords.stemString(keyword);
        BasicDBObject query = new BasicDBObject();
        query.put("_id",keyword);
        MongoCursor<org.bson.Document> cursor = hadoopCollection.find(query).noCursorTimeout(true).iterator();

        List<String> docid = new ArrayList<>();
        List<Integer> freq = new ArrayList<>();
        try {
            System.out.println("Fetched records");

            while (cursor.hasNext()) {

                org.bson.Document row = cursor.next();
                List<org.bson.Document> docs = (List<org.bson.Document>) row.get("result");
                for (org.bson.Document d: docs){
                    docid.add(d.getString("docid"));
                    freq.add(d.getInteger("frequency"));
                }
            }
        } finally {
            cursor.close();
        }

        double idf = Math.log(TweetsCollection.count()/(double)docid.size());
        List<Response> arrayResponse = new ArrayList<Response>();
        for (String id: docid) {
            BasicDBObject docQuery = new BasicDBObject();
            ObjectId ID = new ObjectId(id);
            docQuery.put("_id", ID);
            //docQuery.put("lang","en");
            cursor = TweetsCollection.find(docQuery).limit(1).noCursorTimeout(true).iterator();
            try {
                System.out.println("Fetched records");
                while (cursor.hasNext()) {

                    org.bson.Document row = cursor.next();
                    String tweetBody = row.getString("text");
                    String words[] = tweetBody.split("\\s+");
                    int f = freq.remove(0);
                    double tf = f/(double)words.length;
                    double score = tf*idf;


                    Response tweetResponse = new Response();
                    tweetResponse.tweetId = row.getLong("id");
                    tweetResponse.tweetBody = tweetBody;
                    int val = LuceneIndexing.sentiment.findSentiment(tweetResponse.tweetBody);
                    tweetResponse.sentiment = val;
                    switch (val) {
                        case CoreNLPProtos.Sentiment.STRONG_POSITIVE_VALUE:
                            tweetResponse.sentimentColor = "#28B463";
                            break;
                        case CoreNLPProtos.Sentiment.WEAK_POSITIVE_VALUE:
                            tweetResponse.sentimentColor = "#82E0AA";
                            break;
                        case CoreNLPProtos.Sentiment.STRONG_NEGATIVE_VALUE:
                            tweetResponse.sentimentColor = "#CB4335";
                            break;
                        case CoreNLPProtos.Sentiment.WEAK_NEGATIVE_VALUE:
                            tweetResponse.sentimentColor = "#F1948A";
                            break;
                        case CoreNLPProtos.Sentiment.NEUTRAL_VALUE:
                            tweetResponse.sentimentColor = "#D5DBDB";
                            break;
                        default:
                            tweetResponse.sentimentColor = "#F7F9F9";
                            break;
                    }

                    if(row.get("entities")!=null)
                    {
                        List<org.bson.Document> h = (List<org.bson.Document>)((org.bson.Document)row.get("entities")).get("hashtags");
                        if(h.size()!=0)
                        {
                            List<String> joinedHashtags = new ArrayList<String>();
                            for(org.bson.Document sd: h)
                            {
                                joinedHashtags.add("#"+sd.getString("text"));
                            }
                            tweetResponse.hashtags = String.join("",joinedHashtags);
                        }

                    }

                    tweetResponse.name = ((org.bson.Document)row.get("user")).getString("screen_name");
                    tweetResponse.url = ((org.bson.Document)row.get("user")).getString("profile_image_url");
                    tweetResponse.created = row.getString("created_at");
                    tweetResponse.retweet_count = row.getInteger("retweet_count");
                    tweetResponse.score = (float)score;
                    tweetResponse.location = ((org.bson.Document)row.get("user")).getString("location");
                    if(((List<Double>)((org.bson.Document)row.get("geo")).get("coordinates")).size() > 0) {
                        double lat = ((List<Double>) ((org.bson.Document) row.get("geo")).get("coordinates")).get(0);
                        double longt = ((List<Double>) ((org.bson.Document) row.get("geo")).get("coordinates")).get(1);
                        tweetResponse.latitude = lat;
                        tweetResponse.longitude = longt;
                    }

                    arrayResponse.add(tweetResponse);

                }
                cursor.close();
            } catch (Exception e) {
                throw new NullPointerException();
            }
            finally {
                cursor.close();

            }

            }
        return arrayResponse;
    }
}

public class LuceneIndexing {

    static final String INDEX_PATH = "/Users/harry/Desktop/indexDir";
    static SentimentAnalyzer sentiment = new SentimentAnalyzer();
    public void testWriteIndex(){
        try {
            TweetIndexing lw = new TweetIndexing(INDEX_PATH);
            lw.createIndex();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testLucene(String keyword) throws IOException, ParseException {
        try{
            Directory indexDirectory = FSDirectory.open(Paths.get(INDEX_PATH));
            IndexReader indexReader = DirectoryReader.open(indexDirectory);
            final IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            //Query Parser for searching the Lucene Index
            QueryParser parser = new QueryParser("tweetbody",new StandardAnalyzer());
            Query query = null;

            query = parser.parse(keyword);

            TopDocs topDocs = indexSearcher.search(query, 300);
            ScoreDoc scoreDocs[] = topDocs.scoreDocs;
            for (ScoreDoc d: scoreDocs) {
                int docId = d.doc;
                org.apache.lucene.document.Document document = indexSearcher.doc(docId);


            String location = document.getField("location").stringValue();
            System.out.println(location);

            }
            indexDirectory.close();
            indexReader.close();

        } catch (ParseException e) {
            throw new ParseException();
        }
        catch (IOException e) {
            e.printStackTrace();
            throw new IOException();
        }
        catch (NullPointerException e)
        {
            e.printStackTrace();
            throw new NullPointerException();
        }
        catch (Exception e){
            throw new NullPointerException();
        }

    }

    public APIResponse testQueryLucene(String keyword) throws IOException, ParseException {

        Map<String,Analyzer> analyzerPerField = new HashMap<String,Analyzer>();
        analyzerPerField.put("hashtags", new KeywordAnalyzer());
        PerFieldAnalyzerWrapper aWrapper =
                new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
        try {
            Directory indexDirectory = FSDirectory.open(Paths.get(INDEX_PATH));
            IndexReader indexReader = DirectoryReader.open(indexDirectory);
            final IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            //Query Parser for searching the Lucene Index
            QueryParser parser = new QueryParser("tweetbody", aWrapper);
            Query query = null;

            query = parser.parse(keyword);

            TopDocs topDocs = indexSearcher.search(query, 30);
            ScoreDoc scoreDocs[] = topDocs.scoreDocs;


            List<Response> arrayResponse = new ArrayList<Response>();
            for (ScoreDoc d : scoreDocs) {
                int docId = d.doc;
                org.apache.lucene.document.Document document = indexSearcher.doc(docId);

                Response tweetResponse = new Response();
                tweetResponse.tweetId = document.getField("twid").numericValue().longValue();
                tweetResponse.tweetBody = document.getField("tweetbody").stringValue();
                int val = sentiment.findSentiment(tweetResponse.tweetBody);
                tweetResponse.sentiment = val;
                switch (val) {
                    case CoreNLPProtos.Sentiment.STRONG_POSITIVE_VALUE:
                        tweetResponse.sentimentColor = "#28B463";
                        break;
                    case CoreNLPProtos.Sentiment.WEAK_POSITIVE_VALUE:
                        tweetResponse.sentimentColor = "#82E0AA";
                        break;
                    case CoreNLPProtos.Sentiment.STRONG_NEGATIVE_VALUE:
                        tweetResponse.sentimentColor = "#CB4335";
                        break;
                    case CoreNLPProtos.Sentiment.WEAK_NEGATIVE_VALUE:
                        tweetResponse.sentimentColor = "#F1948A";
                        break;
                    case CoreNLPProtos.Sentiment.NEUTRAL_VALUE:
                        tweetResponse.sentimentColor = "#D5DBDB";
                        break;
                    default:
                        tweetResponse.sentimentColor = "#F7F9F9";
                        break;
                }
                tweetResponse.hashtags = document.getField("hashtags") == null ? "" : document.getField("hashtags").stringValue();
                tweetResponse.name = document.getField("name") == null ? "" : document.getField("name").stringValue();
                tweetResponse.url = document.getField("url") == null ? "" : document.getField("url").stringValue();
                tweetResponse.created = document.getField("created").stringValue();
                tweetResponse.retweet_count = document.getField("retweet_count").numericValue().intValue();
                tweetResponse.score = d.score;
                tweetResponse.location = document.getField("location").stringValue();
                double lat = document.getField("latitude").numericValue().doubleValue();
                double longt = document.getField("longitude").numericValue().doubleValue();
                if (lat == 0.0 && longt == 0.0){
                    tweetResponse.latitude = 0.0;
                    tweetResponse.longitude = 0.0;

                }
                else{
                    tweetResponse.latitude = lat;
                    tweetResponse.longitude = longt;
                }

                arrayResponse.add(tweetResponse);

            }
            //Fetch Most retweeted Docs

            SortField sortF = new SortField("s",SortField.Type.INT);
            Sort sort = new Sort(sortF);

            topDocs = indexSearcher.search(query, 5, sort);
            scoreDocs = topDocs.scoreDocs;


            List<Response> FarrayResponse = new ArrayList<Response>();
            for (ScoreDoc d : scoreDocs) {
                int docId = d.doc;
                org.apache.lucene.document.Document document = indexSearcher.doc(docId);

                Response tweetResponse = new Response();
                tweetResponse.tweetId = document.getField("twid").numericValue().longValue();
                tweetResponse.tweetBody = document.getField("tweetbody").stringValue();
                int val = sentiment.findSentiment(tweetResponse.tweetBody);
                tweetResponse.sentiment = val;
                switch (val) {
                    case CoreNLPProtos.Sentiment.STRONG_POSITIVE_VALUE:
                        tweetResponse.sentimentColor = "#28B463";
                        break;
                    case CoreNLPProtos.Sentiment.WEAK_POSITIVE_VALUE:
                        tweetResponse.sentimentColor = "#82E0AA";
                        break;
                    case CoreNLPProtos.Sentiment.STRONG_NEGATIVE_VALUE:
                        tweetResponse.sentimentColor = "#CB4335";
                        break;
                    case CoreNLPProtos.Sentiment.WEAK_NEGATIVE_VALUE:
                        tweetResponse.sentimentColor = "#F1948A";
                        break;
                    case CoreNLPProtos.Sentiment.NEUTRAL_VALUE:
                        tweetResponse.sentimentColor = "#D5DBDB";
                        break;
                    default:
                        tweetResponse.sentimentColor = "#F7F9F9";
                        break;
                }
                tweetResponse.hashtags = document.getField("hashtags") == null ? "" : document.getField("hashtags").stringValue();
                tweetResponse.name = document.getField("name") == null ? "" : document.getField("name").stringValue();
                tweetResponse.url = document.getField("url") == null ? "" : document.getField("url").stringValue();
                tweetResponse.created = document.getField("created").stringValue();
                tweetResponse.retweet_count = document.getField("retweet_count").numericValue().intValue();
                tweetResponse.score = 0;
                tweetResponse.location = document.getField("location").stringValue();
                double lat = document.getField("latitude").numericValue().doubleValue();
                double longt = document.getField("longitude").numericValue().doubleValue();
                if (lat == 0.0 && longt == 0.0){
                    tweetResponse.latitude = 0.0;
                    tweetResponse.longitude = 0.0;

                }
                else{
                    tweetResponse.latitude = lat;
                    tweetResponse.longitude = longt;
                }

                FarrayResponse.add(tweetResponse);

            }
            indexDirectory.close();
            indexReader.close();

            APIResponse apiR = new APIResponse();
            apiR.setResponse(arrayResponse);
            apiR.setMostRetweeted(FarrayResponse);
            return apiR;

        } catch (ParseException e) {
            throw new ParseException();
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException();
        } catch (NullPointerException e) {
            e.printStackTrace();
            throw new NullPointerException();
        } catch (Exception e) {
            throw new NullPointerException();
        }
    }

    public APIResponse testQueryHadoop(String keyword) {

        HadoopInvertedIndex hw = new HadoopInvertedIndex();
        APIResponse apiResponse = new APIResponse();
        apiResponse.setResponse(hw.fetchFromCollection(keyword));
        return apiResponse;
    }

    public static void main(String args[]){
        LuceneIndexing lt = new LuceneIndexing();
        lt.testWriteIndex();
    }
}
