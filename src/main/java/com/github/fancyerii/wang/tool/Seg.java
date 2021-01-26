package com.github.fancyerii.wang.tool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.antbrains.nlp.wordseg.MMSeg;
import com.antbrains.nlp.wordseg.StringTools;
import com.antbrains.nlp.wordseg.Token;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Seg {
    private static Seg instance;
    static {
        instance = new Seg();
    }

    public static Seg getInstance() {
        return instance;
    }

    private MMSeg mm;
    private Map<String, String[]> fineSegMap;
    private Seg() {

        try {
            InputStream is = this.getClass().getResourceAsStream("/segdict.txt");
            List<String> lines = IOUtils.readLines(is, StandardCharsets.UTF_8);

            List<String> words=new ArrayList<>();

            fineSegMap=new HashMap<>();
            for(String line:lines) {
                line=line.trim().toLowerCase();
                if(line.startsWith("#") || line.isEmpty()) continue;
                String[] tks=line.split("\t");
                if(tks.length>2 || tks.length==0) {
                    log.warn("bad line: "+line);
                    continue;
                }

                words.add(tks[0]);
                if(tks.length==2) {
                    tks[1]=tks[1].trim();
                    if(!tks[1].isEmpty()) {
                        String[] splits=tks[1].split(" +");
                        fineSegMap.put(tks[0], splits);
                    }

                }
            }

            mm = new MMSeg(words);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

    }

    public List<String> seg(String s){
        return seg(s, true, true);
    }

    public List<String> seg(String s, boolean mergeEn, boolean doFineSplit) {
        if (s == null)
            return Collections.emptyList();
        s = s.replaceAll("\\x{0}", " ").trim();
        List<Token> tokens = mm.seg(s, null);
        ArrayList<String> words = new ArrayList<>(tokens.size());
        if (mergeEn) {
            StringBuilder remaining = new StringBuilder("");
            for (Token token : tokens) {
                String w = token.getOrigText();
                if (StringTool.isEnLetter(w)) {
                    remaining.append(w);
                } else {
                    if (remaining.length() > 0) {
                        words.add(remaining.toString());
                        remaining.setLength(0);
                    }
                    if (!StringUtils.isBlank(w)) {
                        words.add(w);
                    }

                }
            }
            if (remaining.length() > 0) {
                words.add(remaining.toString());
            }
        } else {
            for (Token token : tokens) {
                String w = token.getOrigText();
                if (!StringUtils.isBlank(w)) {
                    words.add(w);
                }
            }
        }

        if(doFineSplit) {
            List<String> fineWords=new ArrayList<>(words.size());
            for(String word:words) {
                String[] arr=this.fineSegMap.get(word);
                if(arr==null) {
                    fineWords.add(word);
                }else {
                    for(String w:arr) {
                        fineWords.add(w);
                    }
                }
            }
            return fineWords;
        }

        return words;
    }

    public static void main(String[] args) {

    }

}