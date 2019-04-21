package cpscala.XModel;

import cpscala.JModel.JModel;
import cpscala.JModel.JTab;
import cpscala.JModel.JVar;
import scala.Tuple2;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

import com.alibaba.fastjson.*;

//Ԫ��δ����
public class FDEModel {
    public int num_vars = 0;
    public int num_tabs = 0;
    public int num_OriVars;
    public int num_OriTabs;
    public int num_OriMA;
    public int num_tmp;
    public String fileName;

    public JModel jm;

    public FDEVar[] vars;
    public FDETab[] tabs;
    public AddtionalTabCache[] tmpTabs;

    public int max_arity = Integer.MIN_VALUE;
    public int max_domain_size = Integer.MIN_VALUE;
    public int max_tuples_size = Integer.MIN_VALUE;

    //Լ��scope����
    public ArrayList<Integer>[][] tabsScopeMatrix;
    //����Լ��id����
    public int[][] tabsIDMatrix;

    //������ϱ����ı��
    //��һ����Լ��ID�ڶ����Ǳ���ID
    public boolean[][] commonVarsBoolean;
    private ArrayList<ArrayList<Integer>> addtionTabsVarScopeArray = new ArrayList<>();
    private ArrayList<ArrayList<Tuple2<Integer, Integer>>> addtionTabsTabScopeArray = new ArrayList<>();
    //Ϊ�ɱ���
    private Set<Integer>[] newScopesInt;

    private Set<Integer>[] newTabsScopeInt;

    XModel xm;

    public FDEModel(XModel xm) {
        this.xm = xm;
        initial();
        xm = null;
    }

    public FDEModel(String path, int fmt) throws Exception {
        this.xm = new XModel(path, true, fmt);
        String name = getFileName(path);
        initial();
        xm = null;
    }

    public String toJsonStr() {
        return JSONObject.toJSONString(jm);
    }

    public void toJsonFile(String outputPath) throws Exception {
        String filePath = outputPath + "/" + fileName + ".json";
        JSONWriter writer = new JSONWriter(new FileWriter(filePath));
//        writer.startObject();
        writer.writeObject(jm);
//        writer.endObject();
        writer.close();
//        buildJModel();
//        var jmStr = JSONObject.toJSONString(jm);
//        System.out.println(jmStr);
//        return jmStr;
    }

    String getFileName(String path) {
        fileName = path.substring(path.lastIndexOf("/") + 1, path.lastIndexOf("."));
        return fileName;
    }


    public JModel buildJModel() {
        jm = new JModel();
        jm.maxArity = max_arity;
        jm.maxDomSize = max_domain_size;
        jm.maxTuplesSize = max_tuples_size;
        jm.numOriVars = num_OriVars;
        jm.numOriTabs = num_OriTabs;
        jm.numFactors = num_tmp;
        jm.numVars = num_vars;
        jm.numTabs = num_tabs;

        jm.vars = new JVar[num_vars];
        jm.tabs = new JTab[num_tabs];

        for (int i = 0; i < num_vars; ++i) {
            var fdev = vars[i];
            jm.vars[i] = new JVar(i, fdev.addtional, fdev.size);
        }

        for (int i = 0; i < num_tabs; ++i) {
            var fdet = tabs[i];
            jm.tabs[i] = new JTab(i, fdet.arity, fdet.tuples.length, fdet.scopeInt, fdet.tuples);
        }
        return jm;
    }

    void initial() {
        this.num_OriVars = xm.num_vars;
        this.num_OriTabs = xm.num_tabs;
        this.num_OriMA = xm.max_arity;
        tabsScopeMatrix = new ArrayList[num_OriTabs][num_OriTabs];
        commonVarsBoolean = new boolean[num_OriTabs][];

        for (var i = 0; i < num_OriTabs; ++i) {
            var arity = xm.tabs.get(i).arity;
            commonVarsBoolean[i] = new boolean[arity];
            for (var j = 0; j < arity; ++j) {
                commonVarsBoolean[i][j] = true;
            }
        }

        //��Լ��addtionalTabsCache��Ӧid
        tabsIDMatrix = new int[num_OriTabs][num_OriTabs];
        for (var i = 0; i < num_OriTabs; ++i) {
            for (var j = 0; j < num_OriTabs; ++j) {
                tabsIDMatrix[i][j] = -1;
            }
        }

        buildMatrix();
        build1DScope();

        //������ԭ����
        vars = new FDEVar[num_vars];
        tabs = new FDETab[num_tabs];

        for (var i = 0; i < num_OriVars; ++i) {
            var v = xm.vars.get(i);
            vars[i] = new FDEVar(v, false);
            max_domain_size = Math.max(max_domain_size, vars[i].size);
        }

        buildNewScope();
        buildModel();
    }

    void buildMatrix() {

        for (var i = 0; i < num_OriTabs; ++i) {
            var t0 = xm.tabs.get(i);

            for (var j = 0; j < num_OriTabs; ++j) {
                var t1 = xm.tabs.get(j);
                tabsScopeMatrix[i][j] = new ArrayList<>();

                if (t0.id != t1.id) {

                    int k = 0, l = 0;
                    while (k < t0.arity && l < t1.arity) {
                        var v0 = t0.scopeInt[k];
                        var v1 = t1.scopeInt[l];

                        if (v0 < v1)
                            ++k;
                        else if (v0 > v1)
                            ++l;
                        else {
                            tabsScopeMatrix[i][j].add(v0);
                            ++k;
                            ++l;
                        }
                    }
                } else {
                    //Լ��i = jʲô������
                }
            }
        }

        //����commonVarsBoolean
        for (var i = 0; i < num_OriTabs; ++i) {
            var t0 = xm.tabs.get(i);

            for (var j = 0; j < num_OriTabs; ++j) {
                var t1 = xm.tabs.get(j);
                var tSM = tabsScopeMatrix[i][j];
                //<i, k>, <j, l>��Լ��i�ĵ�k���ı�����Լ��j�ĵ�l������
                //��Ϊ�� ��¼�빫�� ���� �����Ժ�ñ� ɾȥ
                //����1�Ĺ����������ż���commonVarsBoolean
                if (tSM.size() > 1) {
                    for (var vid : tSM) {
                        commonVarsBoolean[i][t0.getVarIndex(vid)] = false;
                        commonVarsBoolean[j][t1.getVarIndex(vid)] = false;
                    }
                }
            }
        }

    }

    //��ά����scopeתһά��
    //����tabsIDMatrix
    //addtionTabsTabScopeArray
    //addtionTabsVarScopeArray
    //����factor variable�ĳ���
    //���� �±�����Լ������
    void build1DScope() {

        for (var i = 0; i < num_OriTabs; ++i) {
            for (var j = 0; j < i; ++j) {
                var aca = tabsScopeMatrix[i][j];

                int k = 0;
                boolean has = false;
                while (k < addtionTabsVarScopeArray.size() && !has) {
                    var cc = addtionTabsVarScopeArray.get(k);
                    //�����ǰ��Լ��aca������е��Ѵ��Լ������ͬ��scope
                    //has���Ϊtrue��������
                    //cc��addtionTabsArrayλ��k����tabsIDMatrix
                    //cc��CScope�����������
                    //addtionTabsArray��addtionTabsScopeArray��λ�����ϸ��Ӧ��
                    if (aca.equals(cc)) {
                        has = true;
                        //k����matrix
                        tabsIDMatrix[i][j] = k;
                        tabsIDMatrix[j][i] = k;
                        var t2 = new Tuple2<>(i, j);
                        addtionTabsTabScopeArray.get(k).add(t2);
                    }
                    ++k;
                }

                //�µģ�û��same scope�ĸ���Լ�������¼Ӵ�Լ�� i j ������
                if (!has && aca.size() > 1) {
                    var t2 = new Tuple2<>(i, j);
                    var tq = new ArrayList<Tuple2<Integer, Integer>>();
                    tq.add(t2);
                    addtionTabsTabScopeArray.add(tq);
                    addtionTabsVarScopeArray.add(aca);
                    var addLength = addtionTabsVarScopeArray.size() - 1;
                    tabsIDMatrix[i][j] = addLength;
                    tabsIDMatrix[j][i] = addLength;
                }
            }
        }

        //factor variable ����
        //������� Ӧ����ԭ��������Լ����
        num_tmp = addtionTabsTabScopeArray.size();
        num_tabs = num_OriTabs + num_tmp;
        num_vars = num_OriVars + num_tmp;
    }

    //ȷ���µ�Լ����Χ
    //��Լ����Χ
    //����AddtionalTabCache
    void buildNewScope() {
        newScopesInt = new TreeSet[num_OriTabs];
        newTabsScopeInt = new TreeSet[num_tmp];
        for (var i = 0; i < num_OriTabs; ++i) {
            newScopesInt[i] = new TreeSet<>();
        }

        for (var i = 0; i < num_tmp; ++i) {
            newTabsScopeInt[i] = new TreeSet<>();
        }

        //������Լ��ɾȥ��������
        for (int i = 0; i < num_OriTabs; ++i) {
            var c = xm.tabs.get(i);
            var scp = c.scopeInt;

            for (var j = 0; j < c.arity; ++j) {
                if (commonVarsBoolean[i][j]) {
                    newScopesInt[i].add(scp[j]);
                }
            }
        }

        tmpTabs = new AddtionalTabCache[num_tmp];
        for (int i = 0; i < num_tmp; ++i) {
            var tabsVarScopeInt = addtionTabsVarScopeArray.get(i);
            var tabsTabScopeArray = addtionTabsTabScopeArray.get(i);
            tmpTabs[i] = new AddtionalTabCache(i, tabsVarScopeInt.size(), tabsVarScopeInt, tabsTabScopeArray);

            for (var tabsTuple : tmpTabs[i].tabScopeArray) {
                newScopesInt[tabsTuple._1].add(tmpTabs[i].vid);
                newScopesInt[tabsTuple._2].add(tmpTabs[i].vid);
                newTabsScopeInt[i].add(tabsTuple._1);
                newTabsScopeInt[i].add(tabsTuple._2);
//                addValue(tmpTabs[i], xm.tabs.get(t0id));
//                addValue(tmpTabs[i], xm.tabs.get(t1id));
            }
        }

        for (var ac : tmpTabs) {
            for (var cid : newTabsScopeInt[ac.id]) {
//                System.out.println(cid);
                var c = xm.tabs.get(cid);
                for (var t : c.tuples) {
                    ac.addValue(t, c.scopeInt);
                }
            }
        }

        for (var vv : tmpTabs) {
            vv.finialize();
        }
    }

    public void buildModel() {
        //���ɱ���
        for (int i = num_OriVars, ii = 0; i < num_vars; ++i, ++ii) {
            vars[i] = tmpTabs[ii].exportToAddtionVar();
            max_domain_size = Math.max(max_domain_size, vars[i].size);
        }

        //��Լ������newScope
        for (int i = 0; i < num_OriTabs; ++i) {
            var c = xm.tabs.get(i);
            var arity = newScopesInt[i].size();
            FDEVar[] scope = new FDEVar[arity];
            int j = 0;
            for (var vid : newScopesInt[i]) {
                scope[j++] = vars[vid];
            }

            var numTuples = c.tuples.length;
            int[][] tuples = new int[numTuples][arity];
            max_tuples_size = Math.max(max_tuples_size, numTuples);

            for (j = 0; j < numTuples; ++j) {
                var t = tuples[j];
                var ori_t = c.tuples[j];

                for (int k = 0; k < arity; ++k) {
                    var vid = scope[k].id;
                    if (vid < num_OriVars) {
                        //�ɱ���
                        t[k] = ori_t[c.getVarIndex(vid)];
                    } else {
                        //�±���
                        var tmpTabID = vid - num_OriVars;
                        t[k] = tmpTabs[tmpTabID].getSTDValue(ori_t, c.scopeInt);
                    }
                }
            }



            tabs[i] = new FDETab(i, "", tuples, scope);
            max_arity = Math.max(max_arity, tabs[i].arity);
        }

        //������Լ��
        for (int i = num_OriTabs, ii = 0; i < num_tabs; ++i, ++ii) {
            tabs[i] = tmpTabs[ii].exportToAddtionTab();
            max_arity = Math.max(max_arity, tabs[i].arity);
            max_tuples_size = Math.max(max_tuples_size, tabs[i].tuples.length);
        }
    }

    public void show() {
//        for (XVar x:vars){
//            x.show();
//        }
        System.out.println("show model: numVars = " + vars.length);
        for (var v : vars) {
            v.show();
        }
        System.out.println("show model: numTabs = " + tabs.length);
        for (var t : tabs) {
            t.show();
        }
//        tabs.get(0).show();
    }

    public class AddtionalTabCache {
        int id;
        int cid;
        int vid;
        long[] scale;
        int arity;
        int oriArity;
        int size;
        //ֵ����
        SortedSet<Long> compValsSet = new TreeSet<>();
        long[] untiMap;
        int[] vals;
        Map<Long, Integer> valsMap = new HashMap<>();

        int[][] tuples;
        int[] piTuple;
        int[] scopeInt;
        FDEVar[] scope;
        ArrayList<Tuple2<Integer, Integer>> tabScopeArray;

        public AddtionalTabCache(int id, int inArity, int[] scopeInt, ArrayList<Tuple2<Integer, Integer>> tabScopeArray) {
            this.id = id;
            this.vid = num_OriVars + id;
            this.cid = num_OriTabs + id;
            this.oriArity = inArity;
            this.arity = inArity + 1;
            this.scopeInt = scopeInt.clone();
            piTuple = new int[arity];
            scopeInt[oriArity] = vid;
            scope = new FDEVar[arity];
            initialScale();

            this.tabScopeArray = tabScopeArray;
        }

        public AddtionalTabCache(int id, int inArity, ArrayList<Integer> scopeArray, ArrayList<Tuple2<Integer, Integer>> tabScopeArray) {
            this.id = id;
            this.vid = num_OriVars + id;
            this.cid = num_OriTabs + id;
            this.oriArity = inArity;
            this.arity = inArity + 1;
            scope = new FDEVar[arity];
            this.scopeInt = new int[arity];
            for (int i = 0; i < oriArity; ++i) {
                this.scopeInt[i] = scopeArray.get(i);
            }
            piTuple = new int[arity];
            scopeInt[oriArity] = vid;

            initialScale();
            this.tabScopeArray = tabScopeArray;
        }


        public void initialScale() {
            scale = new long[oriArity];
            for (int i = oriArity - 1, factor = 1; i >= 0; --i) {
                var v = vars[scopeInt[i]];
                scale[i] = factor;
                factor *= v.size;
            }
        }

        public long getCompoundValues(int[] vls) {
            long comVal = 0;
            for (int i = 0; i < oriArity; ++i) {
                comVal += vls[i] * scale[i];
            }

            return comVal;
        }

        public void getTuple(long idx, int[] t) {
            if (t == null) {
                t = new int[arity];
            }

            for (int i = oriArity - 1; i >= 0; --i) {
                var v = vars[scopeInt[i]];
                t[i] = (int) (idx % v.size);
                idx /= v.size;
            }
        }

        public void addValue(int[] t, int[] scp) {
            getPiTuple(t, scp);
            var value = getCompoundValues(piTuple);
            compValsSet.add(value);
        }

        //scp��������
        public int getSTDValue(int[] t, int[] scp) {
            getPiTuple(t, scp);
            var oriValue = getCompoundValues(piTuple);
            return valsMap.get(oriValue);
        }

        void getPiTuple(int[] t, int[] scp) {
            var i = 0;
            var j = 0;
            //�����scp��scopeInt���Ӽ�
            while (i < scp.length && j < oriArity) {
                if (scopeInt[j] < scp[i])
                    ++j;
                else if (scopeInt[j] > scp[i])
                    ++i;
                else {
                    piTuple[j] = t[i];
                    ++i;
                    ++j;
                }
            }
        }

//        public int getSTDValue(int[] t, ArrayList<Integer> scp) {
//            var i = 0;
//            var j = 0;
//            //�����scp��scopeInt���Ӽ�
//            while (i < scp.size() && j < oriArity) {
//                if (scopeInt[j] < scp.get(i))
//                    ++j;
//                else if (scopeInt[j] > scp.get(i))
//                    ++i;
//                else {
//                    piTuple[j] = t[i];
//                    ++i;
//                    ++j;
//                }
//            }
//
//            var oriValue = getCompoundValues(piTuple);
//            return valsMap.get(oriValue);
//        }

        //����Ԫ�飬������ֵ
        public void finialize() {
            size = compValsSet.size();
            vals = new int[size];
            untiMap = new long[size];
            int i = 0;
            for (var a : compValsSet) {
                untiMap[i++] = a;
            }
            tuples = new int[size][arity];

            for (i = 0; i < size; ++i) {
                vals[i] = i;
                valsMap.put(untiMap[i], i);
                getTuple(untiMap[i], tuples[i]);
                tuples[i][oriArity] = i;
            }

            compValsSet.clear();
        }

        public FDEVar exportToAddtionVar() {
            return new FDEVar(vid, "", vals, true);
        }

        public FDETab exportToAddtionTab() {
            for (int i = 0; i < arity; ++i) {
                scope[i] = vars[scopeInt[i]];
            }
            return new FDETab(cid, "", tuples, scope);
        }

    }

}
