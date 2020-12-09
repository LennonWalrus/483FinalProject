import edu.stanford.nlp.simple.Sentence;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Scanner;

public class Query {
    private static FSDirectory index;
    private static Analyzer analyzer;
    static ArrayList<String> answers = new ArrayList<>();
    static ArrayList<String> answers2 = new ArrayList<>();
    static ArrayList<String> foundAnswers = new ArrayList<>();
    static int topD = 1;

    public static void main(String[] args) {
        if(args.length == 0 || (args.length == 1 && isNumeric(args[0]))){
            if(args.length != 0){
                topD = Integer.parseInt(args[0]);
            }
        }
        else {
            System.out.println("Please enter no arguments for top document or a number x as a argument documents for top x documents");
            System.exit(1);
        }
       grabIndex();
       ArrayList<String> queries = genQueries(Paths.get("src/main/resources/questions.txt"));
       queries = queryLemm(queries);
       luceneAnswers(queries);

    }

    private static void grabIndex(){
        try {
            index = FSDirectory.open(Paths.get("src/main/resources/wiki.lucene"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        analyzer = new StandardAnalyzer();
    }

    private static ArrayList<String> genQueries(Path questions){
        ArrayList<String> queries = new ArrayList<>();
        File qTxt = new File(String.valueOf(questions));
        Scanner inputScanner = null;
        try {
            inputScanner = new Scanner(qTxt);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while(inputScanner.hasNext()){
            String query = "";
            String answer = "";
            query += inputScanner.nextLine() +" ";
            //inputScanner.nextLine();
            query += inputScanner.nextLine();
            answer+= inputScanner.nextLine().toLowerCase();
            inputScanner.nextLine();
            queries.add(query);
            if(answer.contains("|")){
                String[] found = answer.split("\\|");
                answers.add(found[0]);
                answers2.add(found[1]);
            }
            else{
                answers.add(answer);
                answers2.add("none");
            }
            //System.out.println(query);
            //System.out.println(answer);
        }
        return queries;
    }

    private static ArrayList<String> queryLemm(ArrayList<String> old){
        ArrayList<String> stemed = new ArrayList<>();
        for(String pre: old){
            String clean = LineCleaner(pre);
            //System.out.println(clean);
            //System.out.println(clean.split("\\s+").length);
            if(clean.length() != 0) {
                Sentence sent1 = new Sentence(clean);
                //System.out.println(sent1.tokens());
                String lemmas = " ";
                for (String lemm : sent1.lemmas()) {
                    lemmas += lemm + " ";
                }
                //System.out.println(lemmas);
                stemed.add(lemmas);
            }
        }
        return stemed;
    }


    private static String LineCleaner(String line){
        String ret = " ";
        String[] tokens = line.split("[!.,?;\\s]+");
        boolean link = false;
        for(String s : tokens){
            String token = s;

            while(token.startsWith("(") || token.startsWith("=") || token.startsWith("\"")){
                token = token.substring(1,token.length());
            }
            while (token.endsWith(",") || token.endsWith(")") || token.endsWith("?") || token.endsWith(".") || token.endsWith("!") || token.endsWith(";") || token.endsWith(":")||  token.endsWith("=") || token.endsWith("\"" )|| token.endsWith("'") ){
                token = token.substring(0, token.length()-1);
            }
            while(token.contains("-")){
                token = token.substring(0,token.indexOf("-")) + token.substring(token.indexOf("-")+1);
            }
            while(token.contains("(")){
                token = token.substring(0,token.indexOf("(")) + token.substring(token.indexOf("(")+1);
            }
            while(token.contains("\"")){
                token = token.substring(0,token.indexOf("\"")) + token.substring(token.indexOf("\"")+1);
            }
            if(token.equals(","))
                continue;

            if(StopAnalyzer.ENGLISH_STOP_WORDS_SET.contains(token.toLowerCase())) {
                continue;
            }


            ret += token.toLowerCase() + " ";
        }
        return ret.substring(0,ret.length()-1);
    }

    private static void luceneAnswers(ArrayList<String> queries){
        int count = -1;
        float overallScore = 0;
        for(String quest: queries) {
            count++;
            String qstr = "content:";
            System.out.println(quest);
            String[] tokens = quest.split("[!.,?;\\s]+");
            for(String token: tokens){
                if(token.equals(""))
                    continue;
                qstr += token + " OR content:";
            }
            qstr = qstr.substring(0, qstr.length()-12);

            //System.out.println(qstr);
            org.apache.lucene.search.Query q = null;
            IndexReader reader = null;
            IndexSearcher searcher = null;
            TopDocs docs = null;
            int hitsPerPage = topD;
            try {
                q = new QueryParser("content", analyzer).parse(qstr);
                reader = DirectoryReader.open(index);
                //System.out.println(reader.numDocs());
                searcher = new IndexSearcher(reader);
                docs = searcher.search(q, hitsPerPage);
                ScoreDoc[] hits = docs.scoreDocs;
                //System.out.println(hits.length);
                for(int i = 0; i< topD; i++ ){
                    System.out.println("result " + (i+1) + ": \"" +searcher.doc(hits[i].doc).get("title") +"\"" );
                }
                System.out.println( "real: \"" +answers.get(count) +"\"");
                if(!answers2.get(count).equals("none")){
                    System.out.println( "real2: \"" +answers2.get(count) +"\"");
                }
                float score = docMRRCalc(hits,count,searcher);
                overallScore += score;
                System.out.println("MRR Score " +score);
                System.out.println();
                foundAnswers.add(searcher.doc(hits[0].doc).get("title"));
            } catch (ParseException | IOException e) {
                e.printStackTrace();
            }

        }
        float MRR = overallScore/(float) queries.size();
        System.out.println("Overall MRR " + MRR);
    }

    private static float docMRRCalc(ScoreDoc[] hits, int count,IndexSearcher searcher ){
        for(int i = 0; i< topD; i++){
            try {
                if(searcher.doc(hits[i].doc).get("title").equals(answers.get(count))|| searcher.doc(hits[i].doc).get("title").equals(answers2.get(count))){
                    return (float)1/(float)(i+1);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }


}
