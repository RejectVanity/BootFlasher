package com.efojug.bootflasher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.efojug.bootflasher.Utils.SystemPropertiesUtils;
import com.efojug.bootflasher.databinding.FragmentFirstBinding;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    String boot_a;
    String boot_b;
    Boolean Aonly = false;

    public void outputLog(String log) {
        binding.log.setText(binding.log.getText() + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "> " + log + "\n");
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!Objects.equals(SystemPropertiesUtils.getProperty("ro.build.ab_update", ""), "true")) {
            binding.aonlyWarning.setVisibility(View.VISIBLE);
            binding.slot.setVisibility(View.GONE);
            binding.bootbDump.setEnabled(false);
            binding.bootbFlash.setEnabled(false);
            Aonly = true;
        }
        if (SystemPropertiesUtils.getProperty("ro.boot.flash.locked", "1").equals("1") || !SystemPropertiesUtils.getProperty("ro.boot.verifiedbootstate", "green").equals("orange")) {
            binding.notUnlockBootloader.setVisibility(View.VISIBLE);
            binding.bootaFlash.setEnabled(false);
            binding.bootbFlash.setEnabled(false);
        }

        if (getRoot()) {
            binding.slot.setText("当前槽位：" + SystemPropertiesUtils.getProperty("ro.boot.slot_suffix", ""));
            try {
                if (Aonly) {
                    boot_a = exeCmd("readlink -f /dev/block/by-name/boot").get();
                    binding.bootA.setText("boot分区：" + boot_a);
                } else {
                    boot_a = exeCmd("readlink -f /dev/block/by-name/boot_a").get();
                    binding.bootA.setText("boot_a分区：" + boot_a);
                }

            } catch (Exception e) {
                outputLog("获取boot_a分区失败 " + e);
                binding.bootA.setText("失败");
            }
            try {
                boot_b = exeCmd("readlink -f /dev/block/by-name/boot_b").get();
                binding.bootB.setText("boot_b分区：" + boot_b);
            } catch (Exception e) {
                outputLog("获取boot_b分区失败 " + e);
                binding.bootB.setText("失败");
            }
        } else {
            Toast.makeText(getContext(), "未检测到root权限，请给予权限后重试", Toast.LENGTH_LONG).show();
            System.exit(0);
        }

        binding.bootaDump.setOnClickListener(view1 -> dumpImg("a"));
        binding.bootbDump.setOnClickListener(view1 -> dumpImg("b"));

        binding.flash.setOnClickListener(view1 -> {
            binding.flash.setEnabled(false);
            binding.confirm.setChecked(false);
            outputLog("开始刷写");
            flashImg(imgPath, targetPath);
        });

        binding.confirm.setOnCheckedChangeListener((compoundButton, b) -> {
            binding.flash.setEnabled(binding.confirm.isChecked());
        });

        binding.bootaFlash.setOnClickListener(view1 -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "选择镜像文件"), 1);
        });

        binding.bootbFlash.setOnClickListener(view1 -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            startActivityForResult(Intent.createChooser(intent, "选择镜像文件"), 2);
        });
    }

    String imgPath;
    String targetPath;

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            try {
                if (requestCode == 1) {
                    imgPath = "/storage/emulated/0/" + data.getData().getPath().split(":")[1];
                    targetPath = boot_a;
                    binding.confirm.setEnabled(true);
                }
                if (requestCode == 2) {
                    imgPath = "/storage/emulated/0/" + data.getData().getPath().split(":")[1];
                    targetPath = boot_b;
                    binding.confirm.setEnabled(true);
                }
            } catch (Exception e) {
                outputLog("获取路径失败 " + e);
            }
            binding.command.setText(imgPath + " -> " + targetPath);
            outputLog(imgPath + " -> " + targetPath);
        }
    }

    public void dumpImg(String boot_partition) {
        try {
            if (Objects.equals(boot_partition, "a")) {
                exeCmd("blockdev --setrw " + boot_a);
                if (Aonly)
                    exeCmd("dd if=" + boot_a + " of=" + "/storage/emulated/0/Download/boot_$(date +%Y%m%d%H%M%S).img bs=4M;sync");
                else
                    exeCmd("dd if=" + boot_a + " of=" + "/storage/emulated/0/Download/boot_a_$(date +%Y%m%d%H%M%S).img bs=4M;sync");
            } else if (Objects.equals(boot_partition, "b")) {
                exeCmd("blockdev --setrw " + boot_b);
                exeCmd("dd if=" + boot_b + " of=" + "/storage/emulated/0/Download/boot_b_$(date +%Y%m%d%H%M%S).img bs=4M;sync");
            }
            outputLog("正在导出到/Download");
        } catch (Exception e) {
            outputLog("导出失败 " + e);
        }
    }

    public void flashImg(String imgPath, String targetPath) {
        try {
            exeCmd("blockdev --setrw " + targetPath);
            exeCmd("dd if=" + imgPath + " of=" + targetPath + " bs=4M;sync");
            outputLog("刷入成功");
        } catch (Exception e) {
            outputLog("刷入失败 " + e);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    public boolean getRoot() {
        try {
            Runtime.getRuntime().exec("su");
            return true;
        } catch (IOException i) {
            outputLog(i.toString());
            return false;
        }
    }

    public Future<String> exeCmd(String command) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<String> callable = () -> {
            StringBuilder sb = new StringBuilder();
            Process process = Runtime.getRuntime().exec("su -c " + command);
            BufferedReader br = new BufferedReader(new InputStreamReader(new SequenceInputStream(process.getInputStream(), process.getErrorStream()), StandardCharsets.UTF_8));
            String s;
            while ((s = br.readLine()) != null) {
                outputLog(s);
                sb.append(s).append("\n");
            }
            process.waitFor();
            binding.logScrollview.post(() -> binding.logScrollview.fullScroll(View.FOCUS_DOWN));
            return sb.toString();
        };
        Future<String> futureResult = executor.submit(callable);
        executor.shutdown();
        return futureResult;
    }
}