package com.jjy.wgcbackend.entitiy;//import com.baomidou.mybatisplus.generator.FastAutoGenerator;
//import com.baomidou.mybatisplus.generator.config.OutputFile;
//import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
//import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
//
//import java.sql.Types;
//import java.util.Collections;
//
//public static void main(String[] args) {
//    FastAutoGenerator.create("jdbc:postgresql://localhost:5432/wgc_db", "postgres", "root")
//            .globalConfig(builder -> {
//                builder.author("baomidou") // 设置作者
//                        .enableSwagger() // 开启 swagger 模式
//                        .outputDir("D://works//java//wgc"); // 指定输出目录
//            })
//            .dataSourceConfig(builder ->
//                    builder.typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
//                        int typeCode = metaInfo.getJdbcType().TYPE_CODE;
//                        if (typeCode == Types.SMALLINT) {
//                            // 自定义类型转换
//                            return DbColumnType.INTEGER;
//                        }
//                        return typeRegistry.getColumnType(metaInfo);
//                    })
//            )
//            .packageConfig(builder ->
//                    builder.parent("com.jjy.wgc.entitiy") // 设置父包名
//                            .moduleName("system") // 设置父包模块名
//                            .pathInfo(Collections.singletonMap(OutputFile.xml, "D:\\works\\java\\wgc\\src\\main\\java\\com\\jjy\\wgc\\mapper")) // 设置mapperXml生成路径
//            )
//            .strategyConfig(builder -> builder
//                    .entityBuilder()
//                    .enableLombok()
//            )
//            .templateEngine(new FreemarkerTemplateEngine())
//            .execute();
//}