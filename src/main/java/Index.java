import edu.stanford.nlp.simple.Sentence;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Scanner;
//program to index wiki pages placed in text files
public class Index {
    boolean indexExists=false;// if index already exists
    String inputFilePath ="";//location of wiki pages
    Directory index;//index
    StandardAnalyzer analyzer;

    public Index(String inputFile) throws Exception{
        inputFilePath =inputFile;//set the location of the wiki pages
        buildIndex();//to build the index
    }


    public static void main(String[] args) throws Exception {
        Index holder = new Index("src/main/Resources/WikiPages");//set location of wiki pages to resources
    }

    //method to build the lucene index does so by looping though wiki text files and designating and filling wiki pages using lematization
    private void buildIndex() throws IOException {
        analyzer = new StandardAnalyzer();
        index = FSDirectory.open(Paths.get("src/main/resources/wiki.lucene"));
        IndexWriterConfig config = new IndexWriterConfig(analyzer);//set config with analyzer
        IndexWriter w = null;
        boolean first = true;//set for first line of each document
        try {
            w = new IndexWriter(index, config);
        } catch (
                IOException e1) {
            System.out.println("ERROR: creating indexWriter");
        }
        File folder = new File(inputFilePath);
        File[] wikiList = folder.listFiles();//grab list of text files from directory
        for(File file: wikiList) {//loop through each file
            System.out.println(file.getName());//print file name for progress
            try (
                    Scanner inputScanner = new Scanner(file)) {//set up scanner for current text file
                String wLine = "";// for next line
                String[] line = null;//for split of next line
                while (inputScanner.hasNextLine()) {//while there is still more to the file
                    if (first) {//set wLine and line for intial line of each document
                        wLine = inputScanner.nextLine();
                        line = wLine.split("\\s+");
                        first = false;
                    }
                    //check if line is a title and not a file or image
                    if (line[0].startsWith("[[") && !line[0].startsWith("[[File:") && !line[0].startsWith("[[file:") && !line[0].startsWith("[[Image:") && !line[0].startsWith("[[image:")) {
                        boolean titleDone = false;// boolean to loop through longer titles
                        int tCount = 2;
                        String[] cleanT = LineCleanerT(wLine).split("\\s+");//user LineCleanerT to clean the doc title and split up again
                        String title = cleanT[1].substring(2).toLowerCase();//grab title without intial brackets
                        while (!titleDone) {//loop through title tokens
                            if (title.endsWith("]]")) {//if end found
                                titleDone = true;//set boolean
                                title = title.substring(0, title.length() - 2);//cut off ending brackets
                                if(title.endsWith(")")){//take off ending )
                                    while(title.endsWith(")")){
                                        title = title.substring(0,title.length()-1);
                                    }
                                }
                            } else {// add token to token title
                                title += " " + cleanT[tCount].toLowerCase();
                                tCount++;
                            }
                        }
                        String content = "";// to save lematized content of page
                        //System.out.println("Title: "+title);
                        wLine = inputScanner.nextLine();//set input to next line
                        line = wLine.split("\\s+");//split again
                        while (!line[0].startsWith("[[")) {//while in content
                            if (wLine.length() != 0) {//as link as line is not empty
                                //System.out.println(wLine);
                               // System.out.println(wLine.length());
                                String clean = LineCleaner(wLine);//clean the line of punctuation or wiki artifacts
                                //System.out.println(clean);
                                //System.out.println(clean.split("\\s+").length);
                                if(clean.length() != 0) {//if cleaned line not empty
                                    Sentence sent1 = new Sentence(clean);//create NLP sentence object using cleaned line
                                    //System.out.println(sent1.tokens());
                                    String lemmas = " ";//for compounding lemmas
                                    for (String lemm : sent1.lemmas()) {//loopthrough lemmas of tokens in string
                                        lemmas += lemm + " ";//add lemmas to lemmas
                                    }
                                    //System.out.println(lemmas);
                                    content += lemmas;// then add the lemmas to content
                                }
                            }
                            if (inputScanner.hasNextLine()) {//after content check again if thers next line if so set next line
                                wLine = inputScanner.nextLine();
                                line = wLine.split("\\s+");
                                if (line.length == 0) {//check if line empty again
                                    while (line.length == 0) {//loop until not empty
                                        if (inputScanner.hasNextLine()) {
                                            wLine = inputScanner.nextLine();
                                            line = wLine.split("\\s+");
                                        } else {
                                            break;
                                        }
                                    }
                                }
                                //check for file or image
                                if (line[0].startsWith("[[File:") || line[0].startsWith("[[file:")|| line[0].startsWith("[[Image:") || line[0].startsWith("[[image:")) {
                                    //while line is a file or image we skip it
                                    while (line[0].startsWith("[[File:") ||line[0].startsWith("[[file:")|| line[0].startsWith("[[Image:") || line[0].startsWith("[[image:")) {
                                        if (inputScanner.hasNextLine()) {//if next line then set
                                            wLine = inputScanner.nextLine();
                                            line = wLine.split("\\s+");
                                            if (line.length == 0) {//if empty loop until not empty
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
                                        else {//if no next line after file or image check stop for page
                                            break;
                                        }
                                    }
                                }
                            } else {// if no line after last content check then stop for page
                                break;
                            }
                        }
                        //System.out.println("content : " +content)
                        addDoc(w, title, content);//add the wiki page to the lucene index
                    }
                    else {//if title not found loop until a title is found
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
        w.close();//close index writer
        indexExists = true;//index now exists
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

    //method to clean scanner lines for wiki artifacts and punctuation this is where stemming is done for stemming branch
    private String LineCleaner(String line){
        String ret = " ";
        String[] tokens = line.split("[!.,?;\\s]+");//split lines by space and punc
        boolean link = false;//for when we find tpl blocks
        for(String s : tokens){//loop through tokens
            String token = s;
            if(s.equals("#redirect") || s.equals("#REDIRECT") ){//skip redirect wiki tokens
                continue;
            }
            if(s.equals("CATEGORIES:"))//skip category tokens
                continue;
            if(link){//while in a link
                if(s.endsWith("[/tpl]"))//if end of link
                    link = false;//end link
                continue;//else skip tokens in link
            }
            while(token.startsWith("(") || token.startsWith("=") || token.startsWith("\"")){//cut tokens starting with ( = or " until they are not there anymore
                token = token.substring(1,token.length());//remove from start of token
            }
            if(token.startsWith("[tpl]")){//if start of link block
                if(s.length() > 5){//set link to true
                    link = true;
                }
                continue;//skip intial link token
            }
            //while the line ends with any of the below punctuation or symbols
            while (token.endsWith(",") || token.endsWith(")") || token.endsWith("?") || token.endsWith(".") || token.endsWith("!") || token.endsWith(";") || token.endsWith(":")|| token.endsWith("=") || token.endsWith("\"")){
                token = token.substring(0, token.length()-1);//remove last character
            }
            while(token.contains("(")){// while token contains left paren
                token = token.substring(0,token.indexOf("(")) + token.substring(token.indexOf("(")+1);//remove
            }
            while(token.contains("\"")){//while token contains "
                token = token.substring(0,token.indexOf("\"")) + token.substring(token.indexOf("\"")+1);//remove
            }
            token = token.replace("\u00a0","");//replace special end line characters
            token = token.replace(" ","");
            if(token.equals(",") || token.equals(".") || token.equals("") || token.equals(" ") )//if token is just a punctuation
                continue;//skip
           // System.out.println(token.length());
            //System.out.println("\""+token + "\"");
            //check if token is a stop word
            if(StopAnalyzer.ENGLISH_STOP_WORDS_SET.contains(token.toLowerCase())) {
                continue;//if it is skip
            }
            ret += token.toLowerCase() + " ";// at token to return string with space
        }
        return ret.substring(0,ret.length()-1);//return without final space
    }

    //shorter version of LineCleaner for cleaning titles
    private String LineCleanerT(String line){
        String ret = " ";// to return
        String[] tokens = line.split("[!.,?;\\s]+");// split title
        for(String s : tokens){//loop through tokens
            String token = s;
            while(token.startsWith("(") || token.startsWith("=") || token.startsWith("\"")){//remove these punctuations from the start of the token
                token = token.substring(1,token.length());
            }
            //while these tokens are present in the title remove them
            while (token.endsWith(",") || token.endsWith(")") || token.endsWith("?") || token.endsWith(".") || token.endsWith("!") || token.endsWith(";") || token.endsWith(":")|| token.endsWith("=") || token.endsWith("\"")){
                token = token.substring(0, token.length()-1);
            }
            //if token is , skip
            if(token.equals(","))
                continue;

            ret += token.toLowerCase() + " ";// add token with a space to return string
        }
        return ret.substring(0,ret.length()-1);//return without final space
    }

    //method to create and add title and content to a lucene document and then add the doc to the index
    private static void addDoc(IndexWriter w, String title, String info) throws IOException{
        Document doc = new Document();//create a new lucene doc
        doc.add(new TextField("title", title, Field.Store.YES));//set title
        doc.add(new TextField("content",info,Field.Store.YES));//set content
        w.addDocument(doc);//add doc to index.
    }



}
