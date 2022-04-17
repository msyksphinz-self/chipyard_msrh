SRC_VLOG  =
SRC_VLOG += ./msrh/src/riscv_common_pkg.sv
SRC_VLOG += ./msrh/src/riscv_fpu_imc_pkg.sv
SRC_VLOG += ./msrh/src/riscv64_pkg.sv
SRC_VLOG += ./msrh/src/msrh_tiny_conf_pkg.sv
SRC_VLOG += ./msrh/src/msrh_csr_def.svh
SRC_VLOG += ./msrh/src/fpnew/src/common_cells/include/common_cells/registers.svh
# SRC_VLOG += ./msrh/src/decoder_inst_cat.sv
# SRC_VLOG += ./msrh/src/decoder_alu_ctrl.sv
# SRC_VLOG += ./msrh/src/decoder_lsu_ctrl.sv
# SRC_VLOG += ./msrh/src/decoder_bru_ctrl.sv
# SRC_VLOG += ./msrh/src/decoder_csu_ctrl.sv
# SRC_VLOG += ./msrh/src/decoder_reg.sv

SRC_VLOG += $(addprefix ./msrh/src/, $(shell cat msrh/src/fpnew.vf))
SRC_VLOG += $(addprefix ./msrh/src/, $(shell cat msrh/src/filelist.f))
# SRC_VLOG += $(addprefix ./msrh/src/, $(shell cat msrh/verilator_sim/filelist.f))

default:
	$(MAKE) -C msrh/veri_metagross rv64imc_tiny
	echo "\`define RV64" > MSRHCoreBlackbox.preprocessed.sv
	cat $(SRC_VLOG) >> MSRHCoreBlackbox.preprocessed.sv
	sed -i "/\`include/d" MSRHCoreBlackbox.preprocessed.sv
	mkdir -p ../../../../../../sims/verilator/generated-src/chipyard.TestHarness.MSRHConfig/
	ln -sf ../../../../generators/msrh/src/main/resources/vsrc/MSRHCoreBlackbox.preprocessed.sv \
		../../../../../../sims/verilator/generated-src/chipyard.TestHarness.MSRHConfig/
