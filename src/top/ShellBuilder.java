package top;

import parser.Help;

import java.util.List;
import java.util.Map;



public class ShellBuilder {
    
    Map<String, List<String>> arg_map = null;

    String getOneArg(String key){
        List<String> list = (arg_map == null) ? null : arg_map.get(key);
        return (list == null) ? null : list.get(0);
    }

    public static void main(String[] args) {
        System.out.println("Hello world");
        Help.printHelp();
    }
}