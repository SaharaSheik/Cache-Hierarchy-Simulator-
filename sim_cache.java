//package expos;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class sim_cache{
    static class Cache {
        int size, blocksize, assoc, read=0,readMiss=0,write=0, writeMiss=0, writeback=0;
        int tags[], replace[], pointer=0, tree[][], flag=0;
        String address[];
        boolean dirty[];

        Cache(int blocksize, int size, int assoc){
            this.blocksize = blocksize;
            this.size = size;
            this.assoc = assoc;
            int blocks = size/blocksize;
            tags = new int[blocks];
            address = new String[blocks];
            replace = new int[blocks];
            dirty = new boolean[blocks];
            for (int i=0;i<blocks;i++)
            {
                tags[i]=replace[i]=0;
                address[i]="";
                dirty[i]=false;
            }
            if (assoc==0) return;
            tree = new int[blocks/assoc][];
            for (int i=0;i<blocks/assoc;i++)
            {
                tree[i] = new int[assoc-1];
                for (int j=0;j<assoc-1;j++)
                    tree[i][j] = 0;
            }
        }
        public int getIndex(String s)
        {
            return (int)(Long.parseLong(s, 16) % (size/assoc) / (blocksize));
        }
        public int getTag(String s)
        {
            return (int)(Long.parseLong(s, 16) / (size/assoc));
        }
        public void delete(String s){
            int n = find(s);
            if (n>-1){
                tags[n]=0;
                address[n]="";
                replace[n]=0;
                if (dirty[n]) total_traffic++;
                dirty[n]=false;
//                calculatePseudo(n, n);
            }
        }
        public int find(String s){
            int setnum = getIndex(s);
            int tag = getTag(s);
            int i=0;
            for (;i<assoc;i++)
                if(tags[i+setnum*assoc]==tag) break;
            if(i==assoc) return -1;
            else return i+setnum*assoc;
        }
        public int getReplace( int setnum ,String pol){
            switch(pol){
                case "0":
                    int k=0;
                    int rep = replace[setnum*assoc];
                    for (int i=0;i<assoc;i++)
                        if (rep > replace[setnum*assoc+i]){
                            k=i;
                            rep = replace[setnum*assoc+i];
                        }
                    return k;
                case "1":
                    int p=0, n=1;
                    for (int i = (int)(Math.log(assoc)/Math.log(2))-1;i>=0;i--){
                        int m = tree[setnum][n-1];
                        p += (int)Math.pow(2, i)*m;
                        n = n*2 + m;
                    }
                    return p;
                case "2":
                    int r = 0;
                    int d[] = new int[assoc];
                    for(int i=0;i<assoc;i++){
                        int tag = tags[setnum*assoc+i];
//                        d[i] = map.get(s);
//                        d[i] = addr.indexOf(s);
//                        int content = (int)(Long.parseLong(s, 16)/blocksize);
                        d[i] = addr.size();
                        for (int j=0;j<addr.size();j++){
                            String temp = addr.get(j);
                            int index = getIndex( temp );
                            int tagg = getTag(temp);
                            if (index == setnum && tagg == tag){
                                d[i] = j;
                                break;
                            }
                        }
//                        if (d[i]<0) d[i] = 100000;
                    }
                    int temp = d[0];
                    for (int i=1;i<assoc;i++)
                        if(temp<d[i]){temp=d[i];r=i;}
                    return r;
            }
            return 0;
        }
        public void calculatePseudo(int setnum, int n)
        {
            if (assoc==1 || n==1) return;
            int parent = n/2;
            tree[setnum][parent-1] = (n%2==0?1:0);
            calculatePseudo(setnum, parent);
        }
        public String allocate(String s){
            return allocate(s, false);
        }
        public String allocate(String s, boolean f){
            int setnum = getIndex(s);
            int tag = getTag(s);
            int i=0;
            for (;i<assoc;i++)
                if(tags[i+setnum*assoc]==0) break;
            if(i==assoc) {
                i=getReplace(setnum, policy);
                tags[i+setnum*assoc] = tag;
                String t = address[i+setnum*assoc];
                address[i+setnum*assoc] = s;
                replace[i+setnum*assoc] = ++pointer;
                calculatePseudo(setnum, assoc + i);
                flag = 1;
                if (dirty[i+setnum*assoc]){
                    flag = 2;
                    dirty[i+setnum*assoc] = false;
                    writeback++;
                }
                dirty[i+setnum*assoc] = f;
                return t;
            } else {
                tags[i+setnum*assoc] = tag;
                address[i+setnum*assoc] = s;
                replace[i+setnum*assoc] = ++pointer;
                calculatePseudo(setnum, assoc + i);
                flag = 0;
                dirty[i+setnum*assoc] = f;
            }
            return "";
        }
        public void read(int n)
        {
            replace[n] = ++pointer;
            calculatePseudo(n/assoc, assoc+n%assoc);
        }
        public void update(int n)
        {
            replace[n] = ++pointer;
            calculatePseudo(n/assoc, assoc+n%assoc);
            dirty[n] = true;
        }
        public void print(String title){
            System.out.println(title);
            for (int i=0;i<size/blocksize/assoc;i++){
                String s = "";
                for (int j=0;j<assoc;j++){
                    String hex = Integer.toHexString(tags[i*assoc+j]);
                    s += hex + (dirty[i*assoc+j]?" D"+((policy.equals("2")||isL2Available())?"":" ")+"\t":"  \t") + ((hex.length()<5 && !isL2Available())?"\t":"");
                }
                System.out.println("Set     "+Integer.toString(i)+":"+(policy.equals("2")?" ":"")+"\t"+s);
            }
        }
    }
    public static void readRequest(String s, int cache){
        switch(cache){
            case 1:
                L1.read++;
                int n=L1.find(s);
                if(n>-1){
                    L1.read(n);
                }else {
                    L1.readMiss++;

                    String t = L1.allocate(s);
                    if (L1.flag == 1){// deleted address t from L1
                        //do nothing
                    } else if (L1.flag == 2){ // writeback
                        if(isL2Available()) writeRequest(t, 2);
                        else total_traffic++;
                    }
                    if (isL2Available()) readRequest(s, 2);
                    else total_traffic++;
                }
                break;
            case 2:
                L2.read++;
                int m=L2.find(s);
                if(m>-1){
                    L2.read(m);
                }else{
                    L2.readMiss++;
                    total_traffic++;
                    String t = L2.allocate(s);
                    if(prop.equals("1")&&!t.equals("")) L1.delete(t);
                    if ( L2.flag == 1){
                    }else if (L2.flag == 2){
                        total_traffic++;
                    }
                }
                break;
        }
    }
    public static void writeRequest(String s, int cache){
        switch(cache){
            case 1:
                L1.write++;
                int n=L1.find(s);
                if(n>-1){
                    L1.update(n);
                }else {
                    L1.writeMiss++;

                    String t = L1.allocate(s, true);
                    if (L1.flag == 1){// deleted address t from L1
                        //do nothing
                    } else if (L1.flag == 2){ // writeback
                        if(isL2Available()) writeRequest(t, 2);
                        else total_traffic++;
                    }
                    if (isL2Available()) readRequest(s, 2);
                    else total_traffic++;
                }
                break;
            case 2:
                L2.write++;
                int m=L2.find(s);
                if(m>-1){
                    L2.update(m);
                }else{
                    L2.writeMiss++;
                    total_traffic++;
                    String t = L2.allocate(s, true);
                    if(prop.equals("1")&&!t.equals("")) L1.delete(t);
                    if ( L2.flag == 1){ //deleted t from L2
                    }else if (L2.flag == 2){
                        total_traffic++;
                    }
                }
                break;
        }
    }
    public static boolean isL2Available(){
        return L2.size<=0?false:true;
    }
    static int blksize = 16, l1_size = 1024, l1_assoc = 2, l2_size = 8192, l2_assoc = 4, total_traffic=0;
    static String policy = "0", prop = "0", traceFile = "go_trace.txt";
    static Cache L1, L2;
    static ArrayList<String> mode = new ArrayList<String>();
    static ArrayList<String> addr = new ArrayList<String>();
    static HashMap<String, Integer> map = new HashMap<String, Integer>();
    public static void main(String[] args) throws Exception{

        blksize = Integer.parseInt(args[0]);
        l1_size = Integer.parseInt(args[1]);
        l1_assoc = Integer.parseInt(args[2]);
        l2_size = Integer.parseInt(args[3]);
        l2_assoc = Integer.parseInt(args[4]);
        policy = args[5];
        prop = args[6];
        traceFile = args[7];

        L1 = new Cache(blksize, l1_size, l1_assoc);
        L2 = new Cache(blksize, l2_size, l2_assoc);

        Scanner scanner = new Scanner(new File(traceFile));
        int a=0;
        while(scanner.hasNextLine())
        {
            String s = scanner.nextLine();
            if (!s.contains("w")&&!s.contains("r")) continue;
            mode.add(s.split(" ")[0]);
            addr.add(s.split(" ")[1]);
            if (map.containsKey(s.split(" ")[1])) map.put(s.split(" ")[1], map.get(s.split(" ")[1])+1);
            else map.put(s.split(" ")[1], 1);
        }

        while ( addr.size() > 0 ){
            String mod = mode.get(0);
            String s = addr.get(0);
            map.put(s, map.get(s)-1);

            if(mod.contains("w")){
                writeRequest(s, 1);
            } else if(mod.contains("r")){
                readRequest(s, 1);
            }
            mode.remove(0);
            addr.remove(0);
        }
        printSimulator();
        L1.print("===== L1 contents =====");
        if(isL2Available()) L2.print("===== L2 contents =====");
        printResult();
    }
    public static void printSimulator(){
        System.out.println("===== Simulator configuration =====");
        System.out.println("BLOCKSIZE:             " + Integer.toString(blksize));
        System.out.println("L1_SIZE:               " + Integer.toString(l1_size));
        System.out.println("L1_ASSOC:              " + Integer.toString(l1_assoc));
        System.out.println("L2_SIZE:               " + Integer.toString(l2_size));
        System.out.println("L2_ASSOC:              " + Integer.toString(l2_assoc));
	String pol = "LRU";
	if (policy.equals("1")) pol = "Pseudo-LRU";
	else if (policy.equals("2")) pol = "Optimal";
        System.out.println("REPLACEMENT POLICY:    " + pol);
        System.out.println("INCLUSION PROPERTY:    " + (prop.equals("0")?"non-inclusive":"inclusive"));
        System.out.println("trace_file:            " + traceFile);
    }
    public static void printResult(){
        System.out.println("===== Simulation results (raw) =====");
        System.out.println("a. number of L1 reads:        " + Integer.toString(L1.read));
        System.out.println("b. number of L1 read misses:  " + Integer.toString(L1.readMiss));
        System.out.println("c. number of L1 writes:       " + Integer.toString(L1.write));
        System.out.println("d. number of L1 write misses: " + Integer.toString(L1.writeMiss));
        System.out.println("e. L1 miss rate:              " + String.format("%f", (float)(L1.readMiss+L1.writeMiss)/(L1.read+L1.write)));
        System.out.println("f. number of L1 writebacks:   " + Integer.toString(L1.writeback));
        System.out.println("g. number of L2 reads:        " + Integer.toString(L2.read));
        System.out.println("h. number of L2 read misses:  " + Integer.toString(L2.readMiss));
        System.out.println("i. number of L2 writes:       " + Integer.toString(L2.write));
        System.out.println("j. number of L2 write misses: " + Integer.toString(L2.writeMiss));
        System.out.println("k. L2 miss rate:              " + ((L2.read)==0?"0":String.format("%f", (float)(L2.readMiss)/(L2.read))));                             //
        System.out.println("l. number of L2 writebacks:   " + Integer.toString(L2.writeback));
        System.out.println("m. total memory traffic:      " + Integer.toString(total_traffic));
    }
}