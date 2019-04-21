package cpscala.test;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;

public class ForkJoinPoolAction extends RecursiveTask<Integer> {
    private static final long serialVersionUID = -3611254198265061729L;

    public static final int threshold = 2;
    private int start;
    private int end;

    public ForkJoinPoolAction(int start, int end) {
        this.start = start;
        this.end = end;
    }

    @Override
    protected Integer compute() {
        System.out.println("xx");
        int sum = 0;

        //��������㹻С�ͼ�������
        boolean canCompute = (end - start) <= threshold;
        if (canCompute) {
            for (int i = start; i <= end; i++) {
                sum += i;
            }
        } else {
            // ������������ֵ���ͷ��ѳ��������������
            int middle = (start + end) / 2;
            ForkJoinPoolAction leftTask = new ForkJoinPoolAction(start, middle);
            ForkJoinPoolAction rightTask = new ForkJoinPoolAction(middle + 1, end);

            // ִ��������
            leftTask.fork();
            rightTask.fork();

            //�ȴ�����ִ�н����ϲ�����
            int leftResult = leftTask.join();
            int rightResult = rightTask.join();

            //�ϲ�������
            sum = leftResult + rightResult;

        }

        return sum;
//        return 0;
    }

    public static void main(String[] args) {
        ForkJoinPool forkjoinPool = new ForkJoinPool();
        //����һ���������񣬼���1+2+3+4
        ForkJoinPoolAction task = new ForkJoinPoolAction(1, 100);


            //ִ��һ������
            Future<Integer> result = forkjoinPool.submit(task);
            try {
                System.out.println(result.get());
            } catch (Exception e) {
                System.out.println(e);
            }

    }

}