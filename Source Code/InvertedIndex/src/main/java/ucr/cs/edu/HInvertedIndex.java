package ucr.cs.edu;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.StringUtils;
import org.json.*;

public class HInvertedIndex {

    public static class TokenizerMapper
            extends Mapper<Object, Text, Text, Text>{


        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();
        private Text details = new Text();

        private String getLocation_Frequency(String word, String body){
            String loc_freq="";
            String loc="";
            int freq = 0;
            for (String token : body.split("\\s+")) {

                    if (token.equalsIgnoreCase(word)){
                        freq += 1;
                        loc += String.valueOf(body.indexOf(word)) + ",";
                    }
            }
            StringBuilder sb = new StringBuilder(loc);
            sb = sb.deleteCharAt(loc.lastIndexOf(","));
            loc = sb.toString();
            loc_freq = "\"location\":["+loc+"], \"frequency\":"+String.valueOf(freq);
            return loc_freq;
        }
        @Override
        public void map(Object key, Text value, Context context
        ) throws IOException, InterruptedException {

            String line = value.toString().toLowerCase();
            JSONObject obj = new JSONObject(line);
            String body = obj.getString("text");
            String docid = obj.getJSONObject("_id").getString("$oid");
            body = Stopwords.removeStopWords(body);
            body = Stopwords.removeStemmedStopWords(body);

            StringTokenizer itr = new StringTokenizer(body);
            while (itr.hasMoreTokens()) {
                String token = itr.nextToken();
                int index = body.indexOf(token);
                word.set(token);
                String info = getLocation_Frequency(token,body);
                info = "{\"docid\":\""+docid+"\","+info+"}";
                details.set(info);
                context.write(word,details);

            }
        }
    }

    public static class IntSumReducer
            extends Reducer<Text,Text,Text,NullWritable> {

        private Text result = new Text();

        public void reduce(Text key, Iterable<Text> values,
                           Context context
        ) throws IOException, InterruptedException {

            String finalInfo="[";
            for (Text val : values) {
                finalInfo += val.toString()+",";
            }
            StringBuilder sb = new StringBuilder(finalInfo);
            sb = sb.deleteCharAt(finalInfo.lastIndexOf(","));
            finalInfo = sb.toString();
            finalInfo += "]";
            //result.set(finalInfo);
            String Key = key.toString().replaceAll("\"","");
            String json = "{\"_id\":\""+Key.toString()+"\",\"result\":"+finalInfo+"}";
            result.set(json);
            context.write(result, NullWritable.get());
        }
    }

    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        GenericOptionsParser optionParser = new GenericOptionsParser(conf, args);

        Job job = Job.getInstance(conf, "Inverted Index");
        job.setJarByClass(HInvertedIndex.class);
        job.setMapperClass(TokenizerMapper.class);
        //job.setCombinerClass(IntSumReducer.class);
        job.setReducerClass(IntSumReducer.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(NullWritable.class);


        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}