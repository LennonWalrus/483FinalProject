
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.pipeline.*;

import javax.print.attribute.standard.DocumentName;
import javax.sound.sampled.Line;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Scanner;

public class Index {
    boolean indexExists=false;
    String inputFilePath ="";
    Directory index;
    StandardAnalyzer analyzer;

    public Index(String inputFile) throws Exception{
        inputFilePath =inputFile;
        buildIndex();
    }


    public static void main(String[] args) throws Exception {
        Index holder = new Index("src/main/Resources/WikiPages");
    }

    private void buildIndex() throws IOException {
        analyzer = new StandardAnalyzer();
        index = FSDirectory.open(Paths.get("src/main/resources/wiki.lucene"));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter w = null;
        boolean first = true;
        try {
            w = new IndexWriter(index, config);
        } catch (
                IOException e1) {
            System.out.println("ERROR: creating indexWriter");
        }
        File folder = new File(inputFilePath);
        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        File[] wikiList = folder.listFiles();
        for(File file: wikiList) {
            //System.out.println(file.getName());
            try (
                    Scanner inputScanner = new Scanner(file)) {
                String wLine = "";
                String[] line = null;
                while (inputScanner.hasNextLine()) {
                    if (first) {
                        wLine = inputScanner.nextLine();
                        line = wLine.split("\\s+");
                        first = false;
                    }
                    if (line[0].startsWith("[[") && !line[0].startsWith("[[File:") && !line[0].startsWith("[[file:") && !line[0].startsWith("[[Image:") && !line[0].startsWith("[[image:")) {
                        boolean titleDone = false;
                        int tCount = 2;
                        String[] cleanT = LineCleanerT(wLine).split("\\s+");
                        String title = cleanT[1].substring(2).toLowerCase();
                        while (!titleDone) {
                            if (title.endsWith("]]")) {
                                titleDone = true;
                                title = title.substring(0, title.length() - 2);
                                if(title.endsWith(")")){
                                    while(title.endsWith(")")){
                                        title = title.substring(0,title.length()-1);
                                    }
                                }
                            } else {
                                title += " " + cleanT[tCount].toLowerCase();
                                tCount++;
                            }
                        }
                        String content = "";
                        //System.out.println("Title: "+title);
                        wLine = inputScanner.nextLine();
                        line = wLine.split("\\s+");
                        while (!line[0].startsWith("[[")) {
                            /*CoreDocument document = pipeline.processToCoreDocument(LineCleaner(wLine));
                            for (CoreLabel tok : document.tokens()) {//loop through tokens of doc
                                if (tok.lemma().equals("."))
                                    continue;
                                content += tok.lemma() + " ";
                            }
                            */
                            content += LineCleaner(wLine);
                            if (inputScanner.hasNextLine()) {
                                wLine = inputScanner.nextLine();
                                line = wLine.split("\\s+");
                                if (line.length == 0) {
                                    while (line.length == 0) {
                                        if (inputScanner.hasNextLine()) {
                                            wLine = inputScanner.nextLine();
                                            line = wLine.split("\\s+");
                                        } else {
                                            break;
                                        }
                                    }
                                }
                                if (line[0].startsWith("[[File:") || line[0].startsWith("[[file:")|| line[0].startsWith("[[Image:") || line[0].startsWith("[[image:")) {
                                    while (line[0].startsWith("[[File:") ||line[0].startsWith("[[file:")|| line[0].startsWith("[[Image:") || line[0].startsWith("[[image:")) {
                                        if (inputScanner.hasNextLine()) {
                                            wLine = inputScanner.nextLine();
                                            line = wLine.split("\\s+");
                                            if (line.length == 0) {
                                                while (line.length == 0) {
                                                    if (inputScanner.hasNextLine()) {
                                                        wLine = inputScanner.nextLine();
                                                        line = wLine.split("\\s+");
                                                    } else {
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                        else {
                                            break;
                                        }
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                        //System.out.println("content : " +content);
                        addDoc(w, title, content);
                    }
                    else {
                        while (!line[0].startsWith("[[")) {
                            if (inputScanner.hasNextLine()) {
                                wLine = inputScanner.nextLine();
                                line = wLine.split("\\s+");
                            } else {
                                break;
                            }
                        }
                   }

                }
                inputScanner.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("doc done");
            first = true;
        }
        w.close();
        indexExists = true;
        System.out.println("got here");
        IndexReader reader = DirectoryReader.open(index);
        System.out.println("index max " +reader.maxDoc());
        /*for(int i = 0; i<reader.maxDoc();i++){
            Document doc = reader.document(i);
            System.out.println("Title: " + doc.get("title"));
            System.out.println("Content: " + doc.get("content"));
        }
        */
    }

    private String LineCleaner(String line){
        String ret = " ";
        String[] tokens = line.split("[!.,?;\\s]+");
        boolean link = false;
        for(String s : tokens){
            String token = s;
            if(s.equals("#redirect") || s.equals("#REDIRECT") ){
                continue;
            }
            if(s.equals("CATEGORIES:"))
                continue;
            if(link){
                if(s.endsWith("[/tpl]"))
                    link = false;
                continue;
            }
            while(token.startsWith("(") || token.startsWith("=") || token.startsWith("\"")){
                token = token.substring(1,token.length());
            }
            if(token.startsWith("[tpl]")){
                if(s.length() > 5){
                    link = true;
                }
                continue;
            }
            while (token.endsWith(",") || token.endsWith(")") || token.endsWith("?") || token.endsWith(".") || token.endsWith("!") || token.endsWith(";") || token.endsWith(":")|| token.endsWith("=") || token.endsWith("s")|| token.endsWith("\"")){
                token = token.substring(0, token.length()-1);
            }
            if(token.endsWith("ed")){
                token = token.substring(0,token.length()-2);
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
            ret += token.toLowerCase() + " ";
        }
        return ret.substring(0,ret.length()-1);
    }


    private String LineCleanerT(String line){
        String ret = " ";
        String[] tokens = line.split("[!.,?;\\s]+");
        for(String s : tokens){
            String token = s;
            while(token.startsWith("(") || token.startsWith("=") || token.startsWith("\"")){
                token = token.substring(1,token.length());
            }
            while (token.endsWith(",") || token.endsWith(")") || token.endsWith("?") || token.endsWith(".") || token.endsWith("!") || token.endsWith(";") || token.endsWith(":")|| token.endsWith("=") || token.endsWith("\"")){
                token = token.substring(0, token.length()-1);
            }
            if(token.equals(","))
                continue;
            ret += token.toLowerCase() + " ";
        }
        return ret.substring(0,ret.length()-1);
    }


    private static void addDoc(IndexWriter w, String title, String info) throws IOException{
        Document doc = new Document();
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("content",info,Field.Store.YES));
        w.addDocument(doc);
    }



}
