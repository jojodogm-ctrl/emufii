package com.mpds.online

// Exact metadata from tools/nifi2-web/game-identities.mjs. No game payloads.
internal object RoomInvitationPins {
    val games: Map<String, Any?> by lazy {
        @Suppress("UNCHECKED_CAST")
        (RoomDirectory.parseStrictJson(DATA) as Map<String, Any?>)
    }
    private const val DATA = """
{
  "mpds": {
    "profile": "mpds-us-debugmenu-room-v1",
    "pipeline": "mpds-us-debugmenu-room-p4-v2",
    "rom": "07b4ac7550e5ed876dd83578d49441712bbbd8127cdd60155e4d1a87604a5af7",
    "stock": "28ce638914e3304eab95167cd17dc1ef893d226c18f9395683d5cbdf2e35ee2f",
    "cohorts": {
      "2": {
        "set": "bcd3d30142fd92fa092645502d8b56d36c7ac86abce9b8b11be2a35f9e19d296",
        "seats": [
          {
            "aid": 0,
            "core": "94ebc333e5cb3c4799abe4526fb35ff35014eed4a97cf895cc22437ef4d2a3e4"
          },
          {
            "aid": 1,
            "core": "c98a8974ed4f59c448521dba3b933b79fe61a1f7f2c49de5e27c795fc39df9cf"
          }
        ]
      },
      "3": {
        "set": "789d97979f627e57799cb33d5597b2220835feb0d244426f200094895c9d211b",
        "seats": [
          {
            "aid": 0,
            "core": "2b89007a4534bffeaa15ea2cad1739d6785dcaed8a5558a38612d2ef08d14e16"
          },
          {
            "aid": 2,
            "core": "d2dc1be28acef66e5640f03c517bfb7c24870bfb2d0391b97491e3ca027e9703"
          },
          {
            "aid": 1,
            "core": "f471e3408c0b72f6dd4b0c5f20df7fad329f54b6174e2fbdf80ac252cf34ebc4"
          }
        ]
      },
      "4": {
        "set": "54b4b7a1c1f557184493c3589fea2baad7e63bf9c2753740decede207e22b575",
        "seats": [
          {
            "aid": 0,
            "core": "6be7190b9b116cf9444ceccea7a80dec92c368bd3a130079383d2c4a8a6ff875"
          },
          {
            "aid": 3,
            "core": "2363e4fbadfa01dbe90095f69f290395f69382119b68969df8f9e164eccb2986"
          },
          {
            "aid": 1,
            "core": "894e3306f46f0db8e931689153979abf07e2aab458a51896dd9b32ded59d6686"
          },
          {
            "aid": 2,
            "core": "3095b3a3942e5de30ffe9b29c8c3119a2d017024258f8d899301e7650604bbdd"
          }
        ]
      }
    }
  },
  "mkds": {
    "profile": "mkds-us-authority-v1",
    "pipeline": "mkds-authority-menu-p8-q64-pair-v1",
    "rom": "0ea8a91a0ae3e9db26337c25a6a8568f6737fa36c1e0761988c0fa0ecbf210ed",
    "bytes": 33554432,
    "cold": {
      "2": "12d211779bbd5150033318dab300dfab6ae8d8e3ccde3e823585c86e985eb847",
      "3": "685575ce686cb8cb5634a95a88b9653a023438aa0616aea799e257e283a37daa",
      "4": "a02571c43917d9c4729b6c52fa22b18f3bd124ee5272c2245a0a70d1c7e291c0",
      "5": "10bd2889afb90cfa38dba7cd6c68b6a9ccbfb4ed75bd7ff98319616b70718121",
      "6": "492fae2bed55fafd9f5ce1c6299182858d6435232d171ed97e057051abdb77c0",
      "7": "7fe2b2bbc8e810520052ca392298c7d881fc3fbadc9915c1f1872e941f39f100",
      "8": "22b9bd339176306645fd6b91fd19663663f361fc76dce4c8adff3664984a2028"
    },
    "menu": {
      "2": {
        "set": "20e5d0f448505286784dea9f7b229319e641d7ae0c8221823c42ecb5c2759d5d",
        "seats": [
          {
            "aid": 0,
            "core": "ce904892ec40cff2366405447f15c65053fa1b9fc33e2f9fa8ac17ae203cb3ea",
            "boot": "fd965e07724925598bd2481b67d0bef3959974d6077cc89c17ab250094924454"
          },
          {
            "aid": 1,
            "core": "791507805a84252b9680c0e13cbc4ca90882254012ff9c7fbd1c26df363ac2f8",
            "boot": "23c7e9873609502b44f02dd281e60e1fb127e02b9724446d063f510a0fb4fd71"
          }
        ]
      },
      "3": {
        "set": "161392462079674a799b19ad6b3c50b21584a9cbe78e965500e7e748565dfcba",
        "seats": [
          {
            "aid": 0,
            "core": "8702f7337b576d42056387b96ca2f7cd60aa433d50d2b8f90cdc09cb40cf321a",
            "boot": "83ec2acf816e894f99ad7df02b001a02bccc1f9c880a3d2494d1734115ca4d2e"
          },
          {
            "aid": 1,
            "core": "efcc826d7a9c911dbbf20e35e722feed479b0857186fec4eaf6742de11a7a3b8",
            "boot": "e5fefd965496a4042754d88096d4f77e07c50f5223c47e99ab0c4f1fb295c30b"
          },
          {
            "aid": 2,
            "core": "9867409b7ee1223580df78d3944effc21f6eab4799bb4f6e386b40926fee0c99",
            "boot": "e5d1703fc135c7c56d8e8f68e51ac06ef837f9e5aec0d1691c381ccb48dd1f24"
          }
        ]
      },
      "4": {
        "set": "a95c63bdb5bf44e9d9ed3f0e71e175de20fc349cc2c99dbc8a4db1bbc8bc99b9",
        "seats": [
          {
            "aid": 0,
            "core": "94e35fd1ba1d6d3f49222bf7a2e8a66bae948b69a072e13e4292ba227b98aa74",
            "boot": "34ac0b62bcb68e462cfcd69d97ada4dda27872156d25ac97a4b1954b2f14bd94"
          },
          {
            "aid": 1,
            "core": "c7e3c7e7aaa1208e7585257bbab57403ecaf676ea80367a1ccd6f55f35e372cd",
            "boot": "fe1be299be82efe2ee2a298e98c6d33cee0c19c0452a41eef82e3a9b9341345a"
          },
          {
            "aid": 2,
            "core": "ed66b48a36dd9c777a81e39368db96ba43c4178738cc918b98d652327ecd885b",
            "boot": "f98911cae9a09101bce14d725782e52536fa59fe598b720c510918d37c142a71"
          },
          {
            "aid": 3,
            "core": "391dc6d1e18878d1f222f58a1ec088e466c998714c300df17488d01749b267fe",
            "boot": "ad32e6e6534bb50532819828cc6c556713e35c9f6834353ecb20a446f7767423"
          }
        ]
      },
      "5": {
        "set": "1a17a04f570e42d7b6d2dc7e008af3845b81a68328029a8bbf1582ce27fc5ea6",
        "seats": [
          {
            "aid": 0,
            "core": "2dc22d4f43149effc621eca6868ac7bd6dd87ead526a765a5f06a207c43bdb44",
            "boot": "cd59471d490c670928fac6178b0c747850f01cb78f85b3b8757e3eee9a9b60c1"
          },
          {
            "aid": 1,
            "core": "cdb94669204a8fd853c493e44647128392aa000c7e732b31c783ff13159eef03",
            "boot": "39b7699cdc44bea319f1e327edc47a48d03820af2e8b29039bce2fadcdae6043"
          },
          {
            "aid": 2,
            "core": "423b1759950832690b961aaa0df9b58d60982f885b3eb309688caf055f1f62e6",
            "boot": "a034fb02434901a8be82719c7903aed5085924adcb3e7c994d7d5d0727611880"
          },
          {
            "aid": 3,
            "core": "4299f95240f81be03a64a2a601cc65153a6afe47aa96145f2c21d52401b87c6c",
            "boot": "9a1559961439a2ebf67028fd90bf670532a0fb32345e6031fbaaf6efefcb53b1"
          },
          {
            "aid": 4,
            "core": "6b8fd8d0829c51732db2606715cc1efe39ba6bd1d3b6ba2a61fc06eff15ef422",
            "boot": "5c0264f2037f45ff83f4da64ccbb9cdc4cf201aa89db184e9bb8807251bd9a43"
          }
        ]
      },
      "6": {
        "set": "b1b00d5544643132343ca84adbb47ab6ff5d4c935d2598ad3b76a21c331407b0",
        "seats": [
          {
            "aid": 0,
            "core": "caa0eda33743b468484b41dc8d4645cf3a8e3315092f6865520d8a28511d013f",
            "boot": "74f63e61af9f324292847d4fa27333210c046c9bee1b8ece69d203b68dc3d718"
          },
          {
            "aid": 1,
            "core": "065f27048f21b4746ef22db12c32872f992cbdf2fd6079330c10d7d7cc703efa",
            "boot": "5c0b3d466f09aaaabbc3547074054dd366bfcfd3d70734b4e9564311651126b1"
          },
          {
            "aid": 2,
            "core": "2a7a04e02ba6569844204fbb0f13e682495f7f16d7a77bb3a20319472e56a3c8",
            "boot": "0d73c2cfca3d0155dc95807ac59666099f11c65c36db48585096342c9756e436"
          },
          {
            "aid": 3,
            "core": "07a62d11be2c10641f7836d527cc377e6e327200296233258a61f1d4d9958161",
            "boot": "eaa89281adef60a6cbb0546a54a7551dec94e6497dc5684669860b9c151deb8c"
          },
          {
            "aid": 4,
            "core": "a8bc5599b7df590fc691701bf6821bb0f4200532d405dde6c7616e33a9d88c15",
            "boot": "552af9a1d866054759ceb751ba10e4a8edb7cdddfeb3c2fc4606767577a6caf8"
          },
          {
            "aid": 5,
            "core": "d8b2ecd89b3ac7debd76bf37427807a811456421a12c0beb8cdeb68c541c427b",
            "boot": "1c70762805b3584a786d6637eb085bbece2e8479c61feec44c505e6168ce5bd7"
          }
        ]
      },
      "7": {
        "set": "50cba4034c562bfb1bef482f527942acb0dc52ced444732b621ded2fa23c1455",
        "seats": [
          {
            "aid": 0,
            "core": "bdc89181aaa6500e59a5cfdcb5c497b9c04997c8c6e828b359efc343e5684b0d",
            "boot": "7b4801a4185fb12de198643b6ab8e1504f29c119caf2ffea75d3895ac0a3fc78"
          },
          {
            "aid": 1,
            "core": "476bc5dca66c04ac74db2fbc49195dabb39422a84e33477c3a270f5de2ac22d3",
            "boot": "c87a7aaaa92589b2ec81f9511681538c928b830ea43a845fc0bb99f4549ce815"
          },
          {
            "aid": 2,
            "core": "d01274e6cd09479fef67532cd676de471888f3ab905f447c8af2577f544e3577",
            "boot": "355e572f978bbf2135dfbdcfe7f138f359f631419d39fde9972cc35ee061bf9f"
          },
          {
            "aid": 3,
            "core": "cd6f953eeb57c9f206a19650f20522ebf4e0871bd4c2b9a16cf0d280328857ea",
            "boot": "ec3d79500ab3019ebe1a61bebdb152717dd187b4b1eb12efe5ba7ce5c3d5e436"
          },
          {
            "aid": 4,
            "core": "e112c9078dacb93ff0d721f4aa30f866b43b58ccf5b78bd3267d0b2c23e51226",
            "boot": "c192ddc4a9c74c4697546942d2631acbaa3a669c0a42293644a3a9cbc5250d07"
          },
          {
            "aid": 5,
            "core": "d7085d1cf4030289301d323a803881305680d5343f3fe455d50a1c28d22ab492",
            "boot": "3288492f7bd7435d1e32e2f86bfe734b4544741ee175f1b6356ed29a40e580e3"
          },
          {
            "aid": 6,
            "core": "afa46403a14ed8338cc2bad9c8d5474d0229c2fb66b08fd149eb0e1dd1271da6",
            "boot": "81d51f37b5e59aadafd0a1669d2fb0f52a6ebed6ff73500e4afba295418c4180"
          }
        ]
      },
      "8": {
        "set": "aa0e93e8e501da3e64d8bbbca3af9a7e702038716c3591e997b50f862658d4d7",
        "seats": [
          {
            "aid": 0,
            "core": "1d775f6adbd1ffa13b8b3d4bbc3e7179d86e7ce11f83810cf9e9e583ba677d10",
            "boot": "f6cb7b49a542bf322deec31ab6c841769bfd6568752ff2e738c1a9b97b9ba05e"
          },
          {
            "aid": 1,
            "core": "9d9fba1547ff8d7dd213e82b89d6b08b4337e100e76170002b08a3e89ca89cc3",
            "boot": "8ba33bb7cf27982ca5cc4dd727f627ad060b6df65b41f7c0c4fcb7db988ed697"
          },
          {
            "aid": 2,
            "core": "afa26b1ce2afacf259cfe60ea40f52ea05ea96eefb343ded679547018822d565",
            "boot": "baa3115288a39de2ce1e5d8a844974de3296b481f924105d8eee1d7af13b24ee"
          },
          {
            "aid": 3,
            "core": "d3b79566e754bff96b4febd360da1d59b81ffb4c4f6c45caec876dd2e2f6a3a9",
            "boot": "e4a837de3dca612fec93cce61dc35a16efc3c8e0ca5929e421cf2bf53c37af4e"
          },
          {
            "aid": 4,
            "core": "cf8c253559b802beedafb5a4c79c75d06acd2cbdc51e0cbe284e44c80c6d16a2",
            "boot": "7e46613463ed026aa2308cb1ead4c4a9938039cc173e8c699eb5179f9d4279c4"
          },
          {
            "aid": 5,
            "core": "dcce9ad56f792ceb830b6ab72f475ecf42d18ddd1155de126e25d05bee77febd",
            "boot": "f6053860fbae1ef824017c806dde094b327e302b41139d9ac833c87b288e2f69"
          },
          {
            "aid": 6,
            "core": "8b06a23776c5ecade758e572815b05286210ca59b52b36e45651e7cae94398c5",
            "boot": "558ce470707f00f7b38cda537029a6f7dc2ed8d1f50e6afed46c8e064705f423"
          },
          {
            "aid": 7,
            "core": "cbe303e2801b3daf1248076d36ea4d33ee21c9a3dbea46bd34523003a89e592b",
            "boot": "3f39d74777bdb81a4771f6b84c0e314c40290709c815e2f1d969973e6705e480"
          }
        ]
      }
    }
  },
  "dkr": {
    "profile": "dkr-eu-sdk-room-v1",
    "pipeline": "dkr-awdp-sdk3-room-v1",
    "rom": "c29bb5a082bc4c8507e08f7ab6fcb2ef825ba079897e32f487c872172cc9946d",
    "bytes": 33554432,
    "set": "41de92e57dcf38531fead66354b3d0c243831424d7c34740abc2eba7df9dde66"
  }
}
"""
}
