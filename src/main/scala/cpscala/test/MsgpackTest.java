//package cpscala.Experiment;
//
//import org.msgpack.core.ExtensionTypeHeader;
//import org.msgpack.core.MessagePack;
//import org.msgpack.core.MessagePacker;
//import org.msgpack.core.MessageUnpacker;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//
//public class MsgpackTest {
//    public static void main(String[] args) {
//        String filepath = "src/Experiment.txt";
//        try {
//            File tempFile = new File(filepath);
//            boolean a = tempFile.createNewFile();
////            tempFile.deleteOnExit();
//// Write packed data to a file. No need exists to wrap the file stream with BufferedOutputStream, since MessagePacker has its own buffer
//            MessagePacker packer = MessagePack.newDefaultPacker(new FileOutputStream(tempFile));
//            /* �����Ƕ��Զ����������͵Ĵ��*/
//            byte[] extData = "custom data type".getBytes(MessagePack.UTF8);
//            packer.packExtensionTypeHeader((byte) 1, extData.length);  // type number [0, 127], data byte length
//            packer.writePayload(extData);
//            packer.close();
//
//        FileInputStream fileInputStream = new FileInputStream(new File(filepath));
//        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(fileInputStream);
////�Ƚ��Զ������ݵ���Ϣͷ����
////        ExtensionTypeHeader et = unpacker.unpackExtensionTypeHeader();
//////�ж���Ϣ����
////        if (et.getType() == (ExtType.TYPE_TAB)) {
////            int lenth = et.getLength();
////            //�����ȶ�ȡ����������
////            byte[] bytes = new byte[lenth];
////            unpacker.readPayload(bytes);
////            //����tabsjson����?
////            TabsJson tab = new TabsJson();
////            //����unpacker�����������ݽ����java������
////            MessageUnpacker unpacker1 = MessagePack.newDefaultUnpacker(bytes);
////            tab.type = unpacker1.unpackInt();
////            tab.f = unpacker1.unpackString();
////            unpacker1.close();
////        }
////        unpacker.close();
////
////        } catch (Exception ex) {
////            System.out.println(ex);
////        }
//ex
//    }
//}
